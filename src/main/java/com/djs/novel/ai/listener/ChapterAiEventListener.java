package com.djs.novel.ai.listener;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.djs.novel.ai.entity.ChapterEmbedding;
import com.djs.novel.ai.entity.ChapterSummary;
import com.djs.novel.ai.cache.ICacheLayer;
import com.djs.novel.ai.event.ChapterDeletedEvent;
import com.djs.novel.ai.event.ChapterPublishedEvent;
import com.djs.novel.ai.event.ChapterUpdatedEvent;
import com.djs.novel.ai.knowledge.ChapterIndexBuilder;
import com.djs.novel.ai.mapper.ChapterEmbeddingMapper;
import com.djs.novel.ai.mapper.ChapterIndexMapper;
import com.djs.novel.ai.mapper.ChapterSummaryMapper;
import com.djs.novel.ai.mapper.CharacterEventMapper;
import com.djs.novel.ai.service.ICharacterService;
import com.djs.novel.ai.service.IEmbeddingService;
import com.djs.novel.ai.service.ISummaryService;
import com.djs.novel.entity.BookChapter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
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
    private ICacheLayer cacheLayer;

    @Async
    @EventListener
    public void onChapterPublished(ChapterPublishedEvent event) {
        BookChapter chapter = event.getChapter();
        try {
            log.info("开始异步 AI 处理: chapterId={}, bookId={}", chapter.getId(), chapter.getBookId());
            cacheLayer.evictBook(chapter.getBookId());

            summaryService.generateSummary(chapter);
            characterService.extractAndStoreCharacters(chapter);
            chapterIndexBuilder.build(chapter);
            embeddingService.generateAndStoreEmbedding(chapter.getId(), chapter.getBookId(), chapter.getContent());

            log.info("AI 处理完成: chapterId={}", chapter.getId());
        } catch (Exception e) {
            log.error("AI 异步处理失败: chapterId={}, error={}",
                    chapter.getId(), e.getMessage(), e);
        }
    }

    @Async
    @EventListener
    public void onChapterUpdated(ChapterUpdatedEvent event) {
        BookChapter chapter = event.getChapter();
        try {
            log.info("开始异步 AI 重新处理: chapterId={}, bookId={}",
                    chapter.getId(), chapter.getBookId());
            cacheLayer.evictBook(chapter.getBookId());

            summaryService.regenerateSummary(chapter);
            characterService.reExtractForChapter(chapter);
            chapterIndexBuilder.build(chapter);
            embeddingService.generateAndStoreEmbedding(chapter.getId(), chapter.getBookId(), chapter.getContent());

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

            log.info("AI 数据清理完成: chapterId={}", chapterId);
        } catch (Exception e) {
            log.error("AI 数据清理失败: chapterId={}, error={}",
                    chapterId, e.getMessage(), e);
        }
    }
}
