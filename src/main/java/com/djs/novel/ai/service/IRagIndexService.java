package com.djs.novel.ai.service;

import com.djs.novel.entity.BookChapter;

public interface IRagIndexService {

    void rebuildChapterIndex(BookChapter chapter);

    boolean refreshChapterTextOnly(BookChapter chapter);

    void deleteChapterIndex(Long bookId, Long chapterId);
}
