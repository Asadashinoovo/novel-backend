package com.djs.novel.ai.listener;

import com.djs.novel.ai.event.ChapterPublishedEvent;
import com.djs.novel.ai.event.ChapterUpdatedEvent;
import com.djs.novel.ai.service.ICharacterService;
import com.djs.novel.ai.service.ISummaryService;
import com.djs.novel.entity.BookChapter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class ChapterAiEventListener {

    @Autowired
    private ISummaryService summaryService;

    @Autowired
    private ICharacterService characterService;

    @Async
    @EventListener
    public void onChapterPublished(ChapterPublishedEvent event) {
        BookChapter chapter = event.getChapter();
        try {
            log.info("开始异步 AI 处理: chapterId={}, bookId={}", chapter.getId(), chapter.getBookId());

            summaryService.generateSummary(chapter);
            characterService.extractAndStoreCharacters(chapter);

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

            summaryService.regenerateSummary(chapter);
            characterService.reExtractForChapter(chapter);

            log.info("AI 重新处理完成: chapterId={}", chapter.getId());
        } catch (Exception e) {
            log.error("AI 异步重新处理失败: chapterId={}, error={}",
                    chapter.getId(), e.getMessage(), e);
        }
    }
}
