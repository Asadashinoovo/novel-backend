package com.djs.novel.entity;




import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;


import java.time.LocalDateTime;


@Data
@TableName("book_info")
public class BookInfo {

    @TableId(type= IdType.AUTO)
    private Long id;


    private String title;

    private String cover;
    private String description;
    private Integer hotCount;
    private Long authorId;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

}
