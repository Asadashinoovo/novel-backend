package com.djs.novel.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChapterListVO {
    private Long id;

    //计算得出的章节序号
    private Integer chapterNum;

    private String title;

    private Integer wordCount;
    private Integer readCount;
}
