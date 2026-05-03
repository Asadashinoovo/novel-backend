package com.djs.novel.ai.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CharacterTimelineVO {
    private Long id;
    private Long characterId;
    private Long chapterId;
    private String eventDescription;
    private Integer sortOrder;
    private String chapterTitle;

    private Integer readCount;
}
