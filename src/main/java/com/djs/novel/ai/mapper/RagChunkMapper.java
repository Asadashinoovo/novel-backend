package com.djs.novel.ai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.djs.novel.ai.entity.RagChunk;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface RagChunkMapper extends BaseMapper<RagChunk> {

    void deleteByChapterId(@Param("chapterId") Long chapterId);

    List<RagChunk> selectByChapterIdOrderByChunkIndex(@Param("chapterId") Long chapterId);

    List<RagChunk> fulltextVisibleSearch(@Param("bookId") Long bookId,
                                         @Param("maxSortOrder") Integer maxSortOrder,
                                         @Param("question") String question,
                                         @Param("limit") int limit);

    List<RagChunk> likeVisibleSearch(@Param("bookId") Long bookId,
                                     @Param("maxSortOrder") Integer maxSortOrder,
                                     @Param("keyword") String keyword,
                                     @Param("limit") int limit);

    List<RagChunk> getVisibleChunksForVectorSearch(@Param("bookId") Long bookId,
                                                   @Param("maxSortOrder") Integer maxSortOrder);
}
