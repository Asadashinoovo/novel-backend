package com.djs.novel.ai.aspect;

import com.djs.novel.ai.event.ChapterDeletedEvent;
import com.djs.novel.ai.event.ChapterPublishedEvent;
import com.djs.novel.ai.event.ChapterUpdatedEvent;
import com.djs.novel.dto.Result;
import com.djs.novel.entity.BookChapter;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
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

    @AfterReturning(
            pointcut = "execution(* com.djs.novel.service.IChapterService.addChapter(..))",
            returning = "result")
    public void afterChapterAdd(JoinPoint joinPoint, Object result) {
        Result res = (Result) result;
        if (res.getSuccess() != null && res.getSuccess()) {
            BookChapter chapter = (BookChapter) joinPoint.getArgs()[0];
            log.info("章节发布成功, chapterId={}, bookId={}, 触发 AI 异步处理",
                    chapter.getId(), chapter.getBookId());
            eventPublisher.publishEvent(new ChapterPublishedEvent(this, chapter));
        }
    }

    @AfterReturning(
            pointcut = "execution(* com.djs.novel.service.IChapterService.updateChapter(..))",
            returning = "result")
    public void afterChapterUpdate(JoinPoint joinPoint, Object result) {
        Result res = (Result) result;
        if (res.getSuccess() != null && res.getSuccess()) {
            BookChapter chapter = (BookChapter) joinPoint.getArgs()[0];
            log.info("章节更新成功, chapterId={}, bookId={}, 触发 AI 重新生成",
                    chapter.getId(), chapter.getBookId());
            eventPublisher.publishEvent(new ChapterUpdatedEvent(this, chapter));
        }
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
            log.info("章节删除成功, chapterId={}, bookId={}, 触发 AI 数据清理",
                    chapterId, bookId);
            // deleteChapter 传的是 bookId + id，需要构造一个最小 chapter 对象给事件
            com.djs.novel.entity.BookChapter chapter = new com.djs.novel.entity.BookChapter();
            chapter.setId(chapterId);
            chapter.setBookId(bookId);
            eventPublisher.publishEvent(new ChapterDeletedEvent(this, chapter));
        }
    }
}
