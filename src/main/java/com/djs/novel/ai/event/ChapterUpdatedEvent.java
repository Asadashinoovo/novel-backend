package com.djs.novel.ai.event;

import com.djs.novel.entity.BookChapter;
import org.springframework.context.ApplicationEvent;

public class ChapterUpdatedEvent extends ApplicationEvent {

    private final BookChapter chapter;
    private final BookChapter previousChapter;
    private final boolean forceReprocess;

    public ChapterUpdatedEvent(Object source, BookChapter chapter) {
        this(source, chapter, null, true);
    }

    public ChapterUpdatedEvent(Object source, BookChapter chapter, BookChapter previousChapter) {
        this(source, chapter, previousChapter, false);
    }

    public ChapterUpdatedEvent(Object source, BookChapter chapter, BookChapter previousChapter, boolean forceReprocess) {
        super(source);
        this.chapter = chapter;
        this.previousChapter = previousChapter;
        this.forceReprocess = forceReprocess;
    }

    public BookChapter getChapter() {
        return chapter;
    }

    public BookChapter getPreviousChapter() {
        return previousChapter;
    }

    public boolean isForceReprocess() {
        return forceReprocess;
    }
}
