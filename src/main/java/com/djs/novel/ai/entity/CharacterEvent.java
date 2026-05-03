package com.djs.novel.ai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("character_event")
public class CharacterEvent {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long characterId;

    private Long chapterId;

    private Long bookId;

    private String eventDescription;

    private LocalDateTime createdAt;
}
