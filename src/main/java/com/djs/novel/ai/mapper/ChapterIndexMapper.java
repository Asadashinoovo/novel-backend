package com.djs.novel.ai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.djs.novel.ai.entity.ChapterIndex;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ChapterIndexMapper extends BaseMapper<ChapterIndex> {

    List<ChapterIndex> findByEntity(@Param("bookId") Long bookId,
                                    @Param("entityType") String entityType,
                                    @Param("entityName") String entityName);

    List<ChapterIndex> findByBookId(@Param("bookId") Long bookId);

    int deleteByChapterId(@Param("chapterId") Long chapterId);
}
