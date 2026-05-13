package com.djs.novel.ai.search;

public record ChunkSearchQuery(
        Long bookId,
        String question,
        Integer maxSortOrder,
        int candidateLimit,
        int finalTopK
) {
}
