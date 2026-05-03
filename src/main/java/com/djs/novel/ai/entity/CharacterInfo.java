package com.djs.novel.ai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("character_info")
public class CharacterInfo {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long bookId;

    private String characterName;

    private Long firstChapterId;

    private LocalDateTime createdAt;
}
