package com.djs.novel.ai.search;

public record SearchResult(Long chapterId, Integer sortOrder, String title, String snippet, float score) {
}
