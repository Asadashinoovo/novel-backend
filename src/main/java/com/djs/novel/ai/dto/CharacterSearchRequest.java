package com.djs.novel.ai.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CharacterSearchRequest {
    private Long bookId;
    private String characterName;
    private Long maxChapterId;
}
