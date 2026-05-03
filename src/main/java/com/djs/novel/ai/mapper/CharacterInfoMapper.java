package com.djs.novel.ai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.djs.novel.ai.entity.CharacterInfo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface CharacterInfoMapper extends BaseMapper<CharacterInfo> {

    List<CharacterInfo> findByBookIdAndName(@Param("bookId") Long bookId,
                                            @Param("characterName") String characterName);

    List<CharacterInfo> findByBookId(@Param("bookId") Long bookId);
}
