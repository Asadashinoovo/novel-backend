package com.djs.novel.ai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("chapter_index")
public class ChapterIndex {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long chapterId;

    private Long bookId;

    /** 实体类型: CHARACTER / LOCATION / EVENT / CONCEPT / ITEM */
    private String entityType;

    private String entityName;

    /** 关联 ID，如角色 info.id */
    private Long refId;

    private LocalDateTime createdAt;
}
