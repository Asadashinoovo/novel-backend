package com.djs.novel.ai.search;

public record SearchQuery(Long bookId, String question, Integer maxSortOrder, int topK) {
}
