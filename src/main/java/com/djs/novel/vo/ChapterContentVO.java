package com.djs.novel.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChapterContentVO {
    private Long id;
    private Long bookId;

    //计算得出的章节序号
    private Integer chapterNum;

    private String title;

    private String content;
    private Integer wordCount;
    private Integer readCount;
}
