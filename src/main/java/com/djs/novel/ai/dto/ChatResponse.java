package com.djs.novel.ai.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatResponse {
    private String answer;
    private List<SourceInfo> sources;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SourceInfo {
        private Long chapterId;
        private String title;
        private String snippet;
    }
}
