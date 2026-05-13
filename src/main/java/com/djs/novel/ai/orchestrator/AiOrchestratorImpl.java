package com.djs.novel.ai.orchestrator;

import com.djs.novel.ai.cache.ICacheLayer;
import com.djs.novel.ai.client.DeepSeekClient;
import com.djs.novel.ai.dto.ChatRequest;
import com.djs.novel.ai.dto.ChatResponse;
import com.djs.novel.ai.rerank.IRerankService;
import com.djs.novel.ai.search.ChunkSearchQuery;
import com.djs.novel.ai.search.ChunkSearchResult;
import com.djs.novel.ai.search.IChunkSearchEngine;
import com.djs.novel.dto.Result;
import com.djs.novel.entity.BookChapter;
import com.djs.novel.mapper.BookChapterMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
public class AiOrchestratorImpl implements AiOrchestrator {

    @Autowired
    private IChunkSearchEngine chunkSearchEngine;

    @Autowired
    private IRerankService rerankService;

    @Autowired
    private ICacheLayer cacheLayer;

    @Autowired
    private DeepSeekClient deepSeekClient;

    @Autowired
    private BookChapterMapper bookChapterMapper;

    @Value("${novel.ai.chunk.max-candidates:40}")
    private int maxChunkCandidates;

    @Value("${novel.ai.chunk.final-top-k:6}")
    private int finalChunkTopK;

    private static final int QUESTION_MAX_CHARS = 500;

    @Override
    public Result chat(ChatRequest request) {
        if (request == null) {
            return Result.fail("请求不能为空");
        }
        log.info("chat request: bookId={}, question={}, maxChapterId={}",
                request.getBookId(), request.getQuestion(), request.getMaxChapterId());

        if (!StringUtils.hasText(request.getQuestion())) {
            return Result.fail("问题不能为空");
        }
        if (request.getQuestion().length() > QUESTION_MAX_CHARS) {
            return Result.fail("问题过长，请控制在500字以内");
        }
        if (request.getBookId() == null) {
            return Result.fail("书籍ID不能为空");
        }
        if (request.getMaxChapterId() == null) {
            return Result.fail("当前阅读章节不能为空");
        }

        BookChapter currentChapter = bookChapterMapper.selectById(request.getMaxChapterId());
        if (currentChapter == null) {
            return Result.fail("当前阅读章节不存在");
        }
        if (!request.getBookId().equals(currentChapter.getBookId())) {
            return Result.fail("当前阅读章节不属于该书籍");
        }
        Integer maxSortOrder = currentChapter.getSortOrder();
        if (maxSortOrder == null) {
            return Result.fail("当前阅读章节缺少排序信息");
        }
        log.info("chapter visibility guard: chapterId={}, sortOrder={}", currentChapter.getId(), maxSortOrder);

        ChatResponse cached = cacheLayer.get(request.getBookId(), request.getMaxChapterId(),
                request.getQuestion()).orElse(null);
        if (cached != null) {
            return Result.ok(cached);
        }

        ChunkSearchQuery chunkQuery = new ChunkSearchQuery(
                request.getBookId(),
                request.getQuestion(),
                maxSortOrder,
                maxChunkCandidates,
                finalChunkTopK);

        List<ChunkSearchResult> candidates = chunkSearchEngine.search(chunkQuery);
        List<ChunkSearchResult> reranked = rerankService.rerank(request.getQuestion(), candidates);
        List<ChunkSearchResult> finalChunks = reranked.stream()
                .filter(chunk -> chunk.sortOrder() != null && chunk.sortOrder() <= maxSortOrder)
                .limit(finalChunkTopK)
                .toList();

        if (finalChunks.isEmpty()) {
            return Result.fail("根据当前已读内容，暂时无法找到足够信息回答这个问题");
        }

        StringBuilder contextBlock = new StringBuilder();
        List<ChatResponse.SourceInfo> sources = new ArrayList<>();

        for (ChunkSearchResult chunk : finalChunks) {
            contextBlock.append(String.format("[第%d章 - \"%s\" - 片段%d]:\n%s\n\n",
                    chunk.sortOrder(), chunk.chapterTitle(), chunk.chunkIndex() + 1, chunk.content()));

            sources.add(new ChatResponse.SourceInfo(
                    chunk.chapterId(),
                    chunk.chapterTitle(),
                    chunk.content().substring(0, Math.min(200, chunk.content().length()))));
        }

        String systemPrompt = """
                你是一个网文阅读助手，帮助读者理解小说剧情和角色。
                请只基于提供的章节片段回答用户问题。每个片段都标注了章节来源和片段编号。
                规则：
                1. 只基于提供的片段内容回答，不要编造信息。
                2. 如果片段中没有足够信息，诚实地说“根据现有内容，暂时无法回答这个问题”。
                3. 回答时引用具体章节编号作为依据。
                4. 使用中文回答，保持简洁，不要剧透片段中未提及的内容。
                """;

        String userPrompt = String.format("""
                相关章节片段：
                %s

                用户问题：%s
                """, contextBlock, request.getQuestion());

        String answer;
        try {
            answer = deepSeekClient.chatCompletion(systemPrompt, userPrompt);
        } catch (RuntimeException e) {
            log.warn("AI chat call failed: {}", e.getMessage());
            if (isTimeout(e)) {
                return Result.fail("AI 服务请求超时，请稍后重试");
            }
            return Result.fail("AI 服务暂时不可用，请稍后重试");
        }

        if (answer == null || answer.isBlank()) {
            return Result.fail("AI 暂时无法回答，请稍后重试");
        }

        ChatResponse chatResponse = new ChatResponse(answer, sources);
        cacheLayer.put(request.getBookId(), request.getMaxChapterId(),
                request.getQuestion(), chatResponse);

        return Result.ok(chatResponse);
    }

    private boolean isTimeout(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            String message = current.getMessage();
            if (message != null) {
                String lower = message.toLowerCase();
                if (lower.contains("timeout")
                        || lower.contains("timed out")
                        || message.contains("超时")) {
                    return true;
                }
            }
            current = current.getCause();
        }
        return false;
    }
}
