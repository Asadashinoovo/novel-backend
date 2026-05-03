package com.djs.novel.ai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.djs.novel.ai.entity.ChapterSummary;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ChapterSummaryMapper extends BaseMapper<ChapterSummary> {

    ChapterSummary getLatestSummary(@Param("bookId") Long bookId);

    ChapterSummary getSummaryUpToChapter(@Param("bookId") Long bookId,
                                         @Param("chapterId") Long chapterId);

    ChapterSummary getSummaryUpToSortOrder(@Param("bookId") Long bookId,
                                           @Param("sortOrder") Integer sortOrder);
}
