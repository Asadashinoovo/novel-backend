package com.djs.novel.ai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("chapter_summary")
public class ChapterSummary {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long bookId;

    private Long chapterId;

    private String summaryText;

    private String status;

    private String errorMsg;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
