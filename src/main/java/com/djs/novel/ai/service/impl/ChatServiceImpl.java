package com.djs.novel.ai.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.djs.novel.ai.client.DeepSeekClient;
import com.djs.novel.ai.dto.ChatRequest;
import com.djs.novel.ai.dto.ChatResponse;
import com.djs.novel.mapper.BookChapterMapper;
import com.djs.novel.ai.service.IChatService;
import com.djs.novel.dto.Result;
import com.djs.novel.entity.BookChapter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
public class ChatServiceImpl implements IChatService {

    @Autowired
    private DeepSeekClient deepSeekClient;

    @Autowired
    private BookChapterMapper bookChapterMapper;

    private static final int TOP_K = 3;
    private static final int CHAPTER_SNIPPET_MAX_CHARS = 2000;

    @Override
    public Result chat(ChatRequest request) {
        if (!StringUtils.hasText(request.getQuestion())) {
            return Result.fail("问题不能为空");
        }
        if (request.getBookId() == null) {
            return Result.fail("书籍ID不能为空");
        }

        // 1. 关键词搜索：用多窗口 token 构建 LIKE 条件搜索章节内容
        List<BookChapter> matchingChapters = searchChapters(request.getBookId(), request.getQuestion());

        if (matchingChapters.isEmpty()) {
            return Result.fail("未找到相关章节内容，请尝试其他问题");
        }

        // 2. 越章过滤
        Integer maxSortOrder = null;
        if (request.getMaxChapterId() != null) {
            BookChapter maxChapter = bookChapterMapper.selectById(request.getMaxChapterId());
            if (maxChapter != null) {
                maxSortOrder = maxChapter.getSortOrder();
            }
        }

        // 3. 构建上下文
        List<ChapterContext> contexts = new ArrayList<>();
        for (BookChapter chapter : matchingChapters) {
            if (!StringUtils.hasText(chapter.getContent())) {
                continue;
            }
            // 越章保护
            if (maxSortOrder != null && chapter.getSortOrder() > maxSortOrder) {
                continue;
            }

            String snippet = chapter.getContent().length() > CHAPTER_SNIPPET_MAX_CHARS
                    ? chapter.getContent().substring(0, CHAPTER_SNIPPET_MAX_CHARS)
                    : chapter.getContent();

            contexts.add(new ChapterContext(
                    chapter.getId(), chapter.getSortOrder(), chapter.getTitle(), snippet));

            if (contexts.size() >= TOP_K) break;
        }

        if (contexts.isEmpty()) {
            return Result.fail("未找到相关章节内容，请尝试其他问题");
        }

        // 按 sortOrder 排序
        contexts.sort((a, b) -> a.sortOrder().compareTo(b.sortOrder()));

        // 4. 构建 RAG prompt
        StringBuilder contextBlock = new StringBuilder();
        List<ChatResponse.SourceInfo> sources = new ArrayList<>();

        for (ChapterContext ctx : contexts) {
            contextBlock.append(String.format("[第%d章 - \"%s\"]:\n%s\n\n",
                    ctx.chapterId(), ctx.title(), ctx.snippet()));

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

        // 5. 调用 AI
        String answer = deepSeekClient.chatCompletion(systemPrompt, userPrompt);

        if (answer == null || answer.isBlank()) {
            return Result.fail("AI 暂时无法回答，请稍后重试");
        }

        ChatResponse chatResponse = new ChatResponse(answer, sources);
        return Result.ok(chatResponse);
    }

    private record ChapterContext(Long chapterId, Integer sortOrder, String title, String snippet) {}

    /**
     * 用问题文本中的多窗口 token 搜索章节内容
     */
    private List<BookChapter> searchChapters(Long bookId, String question) {
        // 去除空白
        String text = question.replaceAll("\\s+", "").trim();

        // 构建多窗口 LIKE 条件
        String sqlCondition;
        if (text.length() >= 2) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < text.length() - 1; i++) {
                if (sb.length() > 0) sb.append(" OR ");
                sb.append("content LIKE '%").append(text, i, i + 2).append("%'");
            }
            sqlCondition = "(" + sb.toString() + ")";
        } else {
            sqlCondition = "content LIKE '%" + text + "%'";
        }

        QueryWrapper<BookChapter> wrapper = new QueryWrapper<>();
        wrapper.eq("book_id", bookId)
               .apply(sqlCondition)
               .orderByAsc("sort_order")
               .last("LIMIT 30");
        return bookChapterMapper.selectList(wrapper);
    }
}
