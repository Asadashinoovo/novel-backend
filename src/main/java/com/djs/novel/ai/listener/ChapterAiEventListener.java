package com.djs.novel.ai.listener;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.djs.novel.ai.cache.ICacheLayer;
import com.djs.novel.ai.edit.ChapterEditAction;
import com.djs.novel.ai.edit.ChapterEditImpact;
import com.djs.novel.ai.edit.ChapterEditImpactAnalyzer;
import com.djs.novel.ai.entity.ChapterAiState;
import com.djs.novel.ai.entity.ChapterEmbedding;
import com.djs.novel.ai.entity.ChapterSummary;
import com.djs.novel.ai.event.ChapterDeletedEvent;
import com.djs.novel.ai.event.ChapterPublishedEvent;
import com.djs.novel.ai.event.ChapterUpdatedEvent;
import com.djs.novel.ai.knowledge.ChapterIndexBuilder;
import com.djs.novel.ai.mapper.ChapterAiStateMapper;
import com.djs.novel.ai.mapper.ChapterEmbeddingMapper;
import com.djs.novel.ai.mapper.ChapterIndexMapper;
import com.djs.novel.ai.mapper.ChapterSummaryMapper;
import com.djs.novel.ai.mapper.CharacterEventMapper;
import com.djs.novel.ai.service.ICharacterService;
import com.djs.novel.ai.service.IEmbeddingService;
import com.djs.novel.ai.service.IRagIndexService;
import com.djs.novel.ai.service.ISummaryService;
import com.djs.novel.entity.BookChapter;
import com.djs.novel.mapper.BookChapterMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Slf4j
public class ChapterAiEventListener {

    @Autowired
    private ISummaryService summaryService;

    @Autowired
    private ICharacterService characterService;

    @Autowired
    private ChapterIndexBuilder chapterIndexBuilder;

    @Autowired
    private IEmbeddingService embeddingService;

    @Autowired
    private ChapterSummaryMapper chapterSummaryMapper;

    @Autowired
    private ChapterEmbeddingMapper chapterEmbeddingMapper;

    @Autowired
    private CharacterEventMapper characterEventMapper;

    @Autowired
    private ChapterIndexMapper chapterIndexMapper;

    @Autowired
    private BookChapterMapper bookChapterMapper;

    @Autowired
    private ICacheLayer cacheLayer;

    @Autowired
    private IRagIndexService ragIndexService;

    @Autowired
    private ChapterEditImpactAnalyzer editImpactAnalyzer;

    @Autowired
    private ChapterAiStateMapper chapterAiStateMapper;

    @Value("${novel.ai.summary.rebuild-window:3}")
    private int summaryRebuildWindow;

    @Async
    @EventListener
    public void onChapterPublished(ChapterPublishedEvent event) {
        BookChapter chapter = loadFreshChapter(event.getChapter());
        if (chapter == null) {
            return;
        }
        try {
            log.info("开始异步 AI 处理: chapterId={}, bookId={}", chapter.getId(), chapter.getBookId());
            cacheLayer.evictBook(chapter.getBookId());

            summaryService.generateSummary(chapter);
            characterService.extractAndStoreCharacters(chapter);
            chapterIndexBuilder.build(chapter);
            embeddingService.generateAndStoreEmbedding(chapter.getId(), chapter.getBookId(), chapter.getContent());
            ragIndexService.rebuildChapterIndex(chapter);
            saveAiState(chapter, ChapterEditAction.SEMANTIC_REPROCESS, "chapter published");

            log.info("AI 处理完成: chapterId={}", chapter.getId());
        } catch (Exception e) {
            log.error("AI 异步处理失败: chapterId={}, error={}",
                    chapter.getId(), e.getMessage(), e);
        }
    }

    @Async
    @EventListener
    public void onChapterUpdated(ChapterUpdatedEvent event) {
        BookChapter chapter = loadFreshChapter(event.getChapter());
        if (chapter == null) {
            return;
        }
        try {
            log.info("开始异步 AI 重新处理: chapterId={}, bookId={}",
                    chapter.getId(), chapter.getBookId());
            ChapterEditImpact impact = resolveImpact(event, chapter);
            log.info("Chapter edit impact: chapterId={}, action={}, reason={}",
                    chapter.getId(), impact.action(), impact.reason());

            if (impact.action() == ChapterEditAction.NO_CHANGE || impact.action() == ChapterEditAction.SKIP_AI) {
                saveAiState(chapter, impact.action(), impact.reason());
                return;
            }

            if (impact.action() == ChapterEditAction.RAG_TEXT_ONLY) {
                boolean refreshed = ragIndexService.refreshChapterTextOnly(chapter);
                if (!refreshed) {
                    ragIndexService.rebuildChapterIndex(chapter);
                }
                saveAiState(chapter, impact.action(), impact.reason());
                return;
            }

            cacheLayer.evictBook(chapter.getBookId());
            summaryService.regenerateAffectedSummaries(chapter, summaryRebuildWindow);
            characterService.reExtractForChapter(chapter);
            chapterIndexBuilder.build(chapter);
            embeddingService.generateAndStoreEmbedding(chapter.getId(), chapter.getBookId(), chapter.getContent());
            ragIndexService.rebuildChapterIndex(chapter);
            saveAiState(chapter, impact.action(), impact.reason());

            log.info("AI 重新处理完成: chapterId={}", chapter.getId());
        } catch (Exception e) {
            log.error("AI 异步重新处理失败: chapterId={}, error={}",
                    chapter.getId(), e.getMessage(), e);
        }
    }

    @Async
    @EventListener
    @Transactional
    public void onChapterDeleted(ChapterDeletedEvent event) {
        BookChapter chapter = event.getChapter();
        Long chapterId = chapter.getId();
        Long bookId = chapter.getBookId();
        try {
            log.info("开始清理已删除章节的 AI 数据: chapterId={}, bookId={}", chapterId, bookId);
            cacheLayer.evictBook(bookId);
            ragIndexService.deleteChapterIndex(bookId, chapterId);

            // 删除前情提要
            chapterSummaryMapper.delete(
                    new QueryWrapper<ChapterSummary>().eq("chapter_id", chapterId));
            // 删除角色事件
            characterEventMapper.deleteByChapterId(chapterId);
            // 删除知识图谱索引
            chapterIndexMapper.deleteByChapterId(chapterId);
            // 删除向量
            chapterEmbeddingMapper.delete(
                    new QueryWrapper<ChapterEmbedding>().eq("chapter_id", chapterId));
            chapterAiStateMapper.delete(
                    new QueryWrapper<ChapterAiState>().eq("chapter_id", chapterId));

            log.info("AI 数据清理完成: chapterId={}", chapterId);
        } catch (Exception e) {
            log.error("AI 数据清理失败: chapterId={}, error={}",
                    chapterId, e.getMessage(), e);
        }
    }

    private ChapterEditImpact resolveImpact(ChapterUpdatedEvent event, BookChapter freshChapter) {
        if (event.isForceReprocess()) {
            return ChapterEditImpact.semantic("forced reprocess");
        }

        BookChapter previous = event.getPreviousChapter();
        if (previous != null) {
            return editImpactAnalyzer.analyze(
                    previous.getTitle(), previous.getContent(),
                    freshChapter.getTitle(), freshChapter.getContent());
        }

        ChapterAiState state = getAiState(freshChapter.getId());
        String rawHash = editImpactAnalyzer.rawHash(freshChapter.getTitle(), freshChapter.getContent());
        if (state != null && rawHash.equals(state.getRawHash())) {
            return ChapterEditImpact.noChange();
        }
        return ChapterEditImpact.semantic("missing previous content");
    }

    private ChapterAiState getAiState(Long chapterId) {
        if (chapterId == null) {
            return null;
        }
        return chapterAiStateMapper.selectOne(
                new QueryWrapper<ChapterAiState>().eq("chapter_id", chapterId));
    }

    private void saveAiState(BookChapter chapter, ChapterEditAction action, String reason) {
        if (chapter == null || chapter.getId() == null || chapter.getBookId() == null) {
            return;
        }

        ChapterAiState state = getAiState(chapter.getId());
        if (state == null) {
            state = new ChapterAiState();
            state.setBookId(chapter.getBookId());
            state.setChapterId(chapter.getId());
        }

        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        state.setRawHash(editImpactAnalyzer.rawHash(chapter.getTitle(), chapter.getContent()));
        state.setNormalizedHash(editImpactAnalyzer.normalizedHash(chapter.getTitle(), chapter.getContent()));
        state.setSemanticHash(editImpactAnalyzer.semanticHash(chapter.getTitle(), chapter.getContent()));
        state.setLastAction(action.name());
        state.setLastReason(reason);
        state.setProcessedAt(now);
        state.setUpdatedAt(now);

        if (state.getId() == null) {
            chapterAiStateMapper.insert(state);
        } else {
            chapterAiStateMapper.updateById(state);
        }
    }

    private BookChapter loadFreshChapter(BookChapter eventChapter) {
        if (eventChapter == null || eventChapter.getId() == null) {
            log.warn("Skip AI processing because event chapter is missing id");
            return null;
        }
        BookChapter fresh = bookChapterMapper.selectById(eventChapter.getId());
        if (fresh == null) {
            log.warn("Skip AI processing because chapter no longer exists: chapterId={}", eventChapter.getId());
            return null;
        }
        if (eventChapter.getBookId() != null && !eventChapter.getBookId().equals(fresh.getBookId())) {
            log.warn("Skip AI processing because event bookId does not match stored chapter: eventBookId={}, storedBookId={}",
                    eventChapter.getBookId(), fresh.getBookId());
            return null;
        }
        return fresh;
    }
}
