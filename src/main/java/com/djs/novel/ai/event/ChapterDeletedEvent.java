package com.djs.novel.ai.event;

import com.djs.novel.entity.BookChapter;
import org.springframework.context.ApplicationEvent;

public class ChapterDeletedEvent extends ApplicationEvent {

    private final BookChapter chapter;

    public ChapterDeletedEvent(Object source, BookChapter chapter) {
        super(source);
        this.chapter = chapter;
    }

    public BookChapter getChapter() {
        return chapter;
    }
}
