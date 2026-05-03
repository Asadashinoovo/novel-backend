package com.djs.novel.ai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("chapter_embedding")
public class ChapterEmbedding {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long chapterId;

    private Long bookId;

    private String embedding;

    private String model;

    private Integer tokenCount;

    private LocalDateTime createdAt;

    @TableField(exist = false)
    private Integer sortOrder;
}
