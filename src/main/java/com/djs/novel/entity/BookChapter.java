package com.djs.novel.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("book_chapter")
public class BookChapter {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long bookId;

    private Integer sortOrder;

    private String title;

    private String content;

    private Integer wordCount;

    private Integer readCount;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
