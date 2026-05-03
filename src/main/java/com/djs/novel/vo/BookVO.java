package com.djs.novel.vo;

import com.djs.novel.entity.BookType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class BookVO {
    private Long id;
    private String title;
    private Long authorId;
    private String cover;
    private String description;
    private Integer hotCount;

    private String authorName;
    private List<BookType> types;

}
