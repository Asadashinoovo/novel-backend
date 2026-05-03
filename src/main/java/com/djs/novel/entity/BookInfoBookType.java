package com.djs.novel.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("book_info_book_type")
public class BookInfoBookType {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long bookId;

    private Long typeId;

    private LocalDateTime createdAt;
}