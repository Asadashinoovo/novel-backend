package com.djs.novel.ai.aspect;

import com.djs.novel.ai.event.ChapterDeletedEvent;
import com.djs.novel.ai.event.ChapterPublishedEvent;
import com.djs.novel.ai.event.ChapterUpdatedEvent;
import com.djs.novel.dto.Result;
import com.djs.novel.entity.BookChapter;
import com.djs.novel.mapper.BookChapterMapper;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@Aspect
@Component
@Slf4j
public class ChapterPublishAspect {

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private BookChapterMapper bookChapterMapper;

    @AfterReturning(
            pointcut = "execution(* com.djs.novel.service.IChapterService.addChapter(..))",
            returning = "result")
    public void afterChapterAdd(JoinPoint joinPoint, Object result) {
        Result res = (Result) result;
        if (res.getSuccess() != null && res.getSuccess()) {
            BookChapter chapter = (BookChapter) joinPoint.getArgs()[0];
            log.info("Chapter published, trigger AI async processing: chapterId={}, bookId={}",
                    chapter.getId(), chapter.getBookId());
            eventPublisher.publishEvent(new ChapterPublishedEvent(this, chapter));
        }
    }

    @Around("execution(* com.djs.novel.service.IChapterService.updateChapter(..))")
    public Object aroundChapterUpdate(ProceedingJoinPoint joinPoint) throws Throwable {
        BookChapter chapter = (BookChapter) joinPoint.getArgs()[0];
        BookChapter previousChapter = null;
        if (chapter != null && chapter.getId() != null) {
            previousChapter = bookChapterMapper.selectById(chapter.getId());
        }

        Object result = joinPoint.proceed();
        Result res = (Result) result;
        if (res.getSuccess() != null && res.getSuccess()) {
            log.info("Chapter updated, trigger AI reindexing: chapterId={}, bookId={}",
                    chapter.getId(), chapter.getBookId());
            eventPublisher.publishEvent(new ChapterUpdatedEvent(this, chapter, previousChapter));
        }
        return result;
    }

    @AfterReturning(
            pointcut = "execution(* com.djs.novel.service.IChapterService.deleteChapter(..))",
            returning = "result")
    public void afterChapterDelete(JoinPoint joinPoint, Object result) {
        Result res = (Result) result;
        if (res.getSuccess() != null && res.getSuccess()) {
            Object[] args = joinPoint.getArgs();
            Long bookId = (Long) args[0];
            Long chapterId = (Long) args[1];
            log.info("Chapter deleted, trigger AI cleanup: chapterId={}, bookId={}", chapterId, bookId);
            BookChapter chapter = new BookChapter();
            chapter.setId(chapterId);
            chapter.setBookId(bookId);
            eventPublisher.publishEvent(new ChapterDeletedEvent(this, chapter));
        }
    }
}
