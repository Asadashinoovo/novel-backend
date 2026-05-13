package com.djs.novel.ai.chunk;

public record ChapterChunk(
        int chunkIndex,
        String content,
        int startOffset,
        int endOffset
) {
}
