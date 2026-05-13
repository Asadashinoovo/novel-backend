package com.djs.novel.ai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("chapter_ai_state")
public class ChapterAiState {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long bookId;

    private Long chapterId;

    private String rawHash;

    private String normalizedHash;

    private String semanticHash;

    private String lastAction;

    private String lastReason;

    private LocalDateTime processedAt;

    private LocalDateTime updatedAt;
}
