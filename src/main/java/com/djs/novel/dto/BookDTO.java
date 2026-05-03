package com.djs.novel.dto;

import com.djs.novel.entity.BookType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
@Data
@AllArgsConstructor
@NoArgsConstructor
public class BookDTO {

    private Long id;

    private String title;

    private String cover;
    private String description;
    private Integer hotCount;
    private Long authorId;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private List<BookType> types;

}
