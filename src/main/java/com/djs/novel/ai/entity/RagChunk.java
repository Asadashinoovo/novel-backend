package com.djs.novel.ai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("rag_chunk")
public class RagChunk {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long bookId;

    private Long chapterId;

    private Integer sortOrder;

    private Integer chunkIndex;

    private String content;

    private String contentHash;

    private Integer startOffset;

    private Integer endOffset;

    private String embedding;

    private String embeddingModel;

    private Integer tokenCount;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
