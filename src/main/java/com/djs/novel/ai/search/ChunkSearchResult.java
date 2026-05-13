package com.djs.novel.ai.search;

public record ChunkSearchResult(
        Long chunkId,
        Long bookId,
        Long chapterId,
        Integer sortOrder,
        Integer chunkIndex,
        String chapterTitle,
        String content,
        float score,
        String source
) {
    public ChunkSearchResult withScoreAndSource(float newScore, String newSource) {
        return new ChunkSearchResult(chunkId, bookId, chapterId, sortOrder, chunkIndex,
                chapterTitle, content, newScore, newSource);
    }
}
