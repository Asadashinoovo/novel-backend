package com.djs.novel.ai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.djs.novel.ai.entity.CharacterEvent;
import com.djs.novel.ai.dto.CharacterTimelineVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface CharacterEventMapper extends BaseMapper<CharacterEvent> {

    List<CharacterTimelineVO> getEventsByCharacterId(@Param("characterId") Long characterId,
                                                     @Param("maxSortOrder") Integer maxSortOrder);

    int deleteByChapterId(@Param("chapterId") Long chapterId);
}
