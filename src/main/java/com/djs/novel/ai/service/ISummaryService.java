package com.djs.novel.ai.service;

import com.djs.novel.dto.Result;
import com.djs.novel.entity.BookChapter;

public interface ISummaryService {

    /** 异步生成章节前情提要 */
    void generateSummary(BookChapter chapter);

    /** 重新生成（章节更新时） */
    void regenerateSummary(BookChapter chapter);

    int regenerateAffectedSummaries(BookChapter changedChapter, int rebuildWindow);

    /** 获取最新摘要 */
    Result getLatestSummary(Long bookId);

    /** 获取指定章节之前的摘要 */
    Result getSummaryUpToChapter(Long bookId, Long chapterId);
}
