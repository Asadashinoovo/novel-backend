package com.djs.novel.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("book_type")
public class BookType {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String typeName;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}