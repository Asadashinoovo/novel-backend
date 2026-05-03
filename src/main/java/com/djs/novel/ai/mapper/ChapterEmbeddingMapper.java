package com.djs.novel.ai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.djs.novel.ai.entity.ChapterEmbedding;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ChapterEmbeddingMapper extends BaseMapper<ChapterEmbedding> {

    List<ChapterEmbedding> getEmbeddingsByBookId(@Param("bookId") Long bookId);
}
