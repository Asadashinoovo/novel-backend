package com.djs.novel.ai.orchestrator;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.djs.novel.ai.cache.ICacheLayer;
import com.djs.novel.ai.client.DeepSeekClient;
import com.djs.novel.ai.dto.ChatRequest;
import com.djs.novel.ai.dto.ChatResponse;
import com.djs.novel.ai.search.ISearchEngine;
import com.djs.novel.ai.search.SearchQuery;
import com.djs.novel.ai.search.SearchResult;
import com.djs.novel.dto.Result;
import com.djs.novel.entity.BookChapter;
import com.djs.novel.mapper.BookChapterMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
public class AiOrchestratorImpl implements AiOrchestrator {

    @Autowired
    private ISearchEngine searchEngine;

    @Autowired
    private ICacheLayer cacheLayer;

    @Autowired
    private DeepSeekClient deepSeekClient;

    @Autowired
    private BookChapterMapper bookChapterMapper;

    private static final int TOP_K = 3;
    private static final int CHAPTER_SNIPPET_MAX_CHARS = 800;

    @Override
    public Result chat(ChatRequest request) {
        log.info("chat request: bookId={}, question={}, maxChapterId={}",
                request.getBookId(), request.getQuestion(), request.getMaxChapterId());

        if (!StringUtils.hasText(request.getQuestion())) {
            return Result.fail("问题不能为空");
        }
        if (request.getBookId() == null) {
            return Result.fail("书籍ID不能为空");
        }

        // 1. 缓存检查（key 包含 maxChapterId，确保不同阅读位置不串缓存）
        String cached = cacheLayer.get(request.getBookId(), request.getMaxChapterId(),
                request.getQuestion()).orElse(null);
        if (cached != null) {
            ChatResponse chatResponse = new ChatResponse(cached, List.of());
            return Result.ok(chatResponse);
        }

        // 2. 获取当前章节（越章保护）
        Integer maxSortOrder = null;
        BookChapter currentChapter = null;
        if (request.getMaxChapterId() != null) {
            currentChapter = bookChapterMapper.selectById(request.getMaxChapterId());
            if (currentChapter != null) {
                maxSortOrder = currentChapter.getSortOrder();
                log.info("越章保护生效: chapterId={}, sortOrder={}",
                        currentChapter.getId(), maxSortOrder);
            } else {
                log.warn("maxChapterId={} 对应的章节不存在，越章保护失效", request.getMaxChapterId());
            }
        } else {
            log.warn("maxChapterId 为空，越章保护未启用！前端未传当前阅读位置");
        }

        // 3. 检索
        SearchQuery searchQuery = new SearchQuery(
                request.getBookId(), request.getQuestion(), maxSortOrder, TOP_K);
        List<SearchResult> searchResults = searchEngine.search(searchQuery);

        // 4. 构建上下文
        List<ChapterContext> contexts = new ArrayList<>();
        java.util.Set<Long> addedChapterIds = new java.util.HashSet<>();

        // 4a. 始终优先包含当前章节
        if (currentChapter != null && StringUtils.hasText(currentChapter.getContent())) {
            addChapterSnippet(currentChapter, contexts, addedChapterIds);

            // 兜底：也加入最近的前置章节
            if (contexts.size() < TOP_K) {
                List<BookChapter> priorChapters = bookChapterMapper.selectList(
                        new QueryWrapper<BookChapter>()
                                .eq("book_id", currentChapter.getBookId())
                                .lt("sort_order", currentChapter.getSortOrder())
                                .orderByDesc("sort_order")
                                .last("LIMIT " + (TOP_K - contexts.size())));
                for (BookChapter prior : priorChapters) {
                    if (contexts.size() >= TOP_K) break;
                    if (StringUtils.hasText(prior.getContent())) {
                        addChapterSnippet(prior, contexts, addedChapterIds);
                    }
                }
            }
        }

        // 4b. 加入检索匹配的章节（批量查询避免 N+1）
        List<Long> idsToFetch = new ArrayList<>();
        for (SearchResult sr : searchResults) {
            if (contexts.size() >= TOP_K) break;
            if (addedChapterIds.contains(sr.chapterId())) continue;
            idsToFetch.add(sr.chapterId());
        }
        if (!idsToFetch.isEmpty()) {
            List<BookChapter> fetchedChapters = bookChapterMapper.selectBatchIds(idsToFetch);
            for (BookChapter chapter : fetchedChapters) {
                if (contexts.size() >= TOP_K) break;
                if (chapter == null || !StringUtils.hasText(chapter.getContent())) continue;
                if (maxSortOrder != null
                        && chapter.getSortOrder() != null
                        && chapter.getSortOrder() > maxSortOrder) {
                    log.warn("越章拦截: 搜索结果包含超出阅读位置的章节 chapterId={}, sortOrder={}, maxSortOrder={}",
                            chapter.getId(), chapter.getSortOrder(), maxSortOrder);
                    continue;
                }
                addChapterSnippet(chapter, contexts, addedChapterIds);
            }
        }

        // 4c. 无结果
        if (contexts.isEmpty()) {
            return Result.fail("未找到相关章节内容，请尝试其他问题");
        }

        // 按 sortOrder 排序
        contexts.sort((a, b) -> a.sortOrder().compareTo(b.sortOrder()));

        // 5. 构建 RAG prompt
        StringBuilder contextBlock = new StringBuilder();
        List<ChatResponse.SourceInfo> sources = new ArrayList<>();

        for (ChapterContext ctx : contexts) {
            contextBlock.append(String.format("[第%d章 - \"%s\"]:\n%s\n\n",
                    ctx.sortOrder(), ctx.title(), ctx.snippet()));

            sources.add(new ChatResponse.SourceInfo(
                    ctx.chapterId(), ctx.title(),
                    ctx.snippet().substring(0, Math.min(200, ctx.snippet().length()))));
        }

        String systemPrompt = """
                你是一个网文阅读助手，帮助读者理解小说剧情和角色。
                请基于以下提供的章节摘录内容回答用户的问题。每个摘录都标注了其章节来源。
                规则：
                1. 只基于提供的摘录内容回答，不要编造信息
                2. 如果摘录中没有足够信息，诚实地说"根据现有内容，暂时无法回答这个问题"
                3. 回答时引用具体的章节编号作为依据
                4. 使用中文回答，保持简洁，不要剧透摘录中未提及的内容
                """;

        String userPrompt = String.format("""
                相关章节内容：
                %s

                用户问题：%s
                """, contextBlock.toString(), request.getQuestion());

        // 6. 调用 LLM
        String answer = deepSeekClient.chatCompletion(systemPrompt, userPrompt);

        if (answer == null || answer.isBlank()) {
            return Result.fail("AI 暂时无法回答，请稍后重试");
        }

        // 7. 写缓存（key 包含 maxChapterId）
        cacheLayer.put(request.getBookId(), request.getMaxChapterId(),
                request.getQuestion(), answer);

        ChatResponse chatResponse = new ChatResponse(answer, sources);
        return Result.ok(chatResponse);
    }

    private record ChapterContext(Long chapterId, Integer sortOrder, String title, String snippet) {}

    private void addChapterSnippet(BookChapter chapter, List<ChapterContext> contexts,
                                   java.util.Set<Long> addedIds) {
        String snippet = chapter.getContent().length() > CHAPTER_SNIPPET_MAX_CHARS
                ? chapter.getContent().substring(0, CHAPTER_SNIPPET_MAX_CHARS)
                : chapter.getContent();
        contexts.add(new ChapterContext(
                chapter.getId(), chapter.getSortOrder(), chapter.getTitle(), snippet));
        addedIds.add(chapter.getId());
    }
}
