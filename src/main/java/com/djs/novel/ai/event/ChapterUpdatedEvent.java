package com.djs.novel.ai.event;

import com.djs.novel.entity.BookChapter;
import org.springframework.context.ApplicationEvent;

public class ChapterUpdatedEvent extends ApplicationEvent {

    private final BookChapter chapter;

    public ChapterUpdatedEvent(Object source, BookChapter chapter) {
        super(source);
        this.chapter = chapter;
    }

    public BookChapter getChapter() {
        return chapter;
    }
}
