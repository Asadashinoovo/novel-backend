package com.djs.novel.ai.chunk;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChapterChunkerTest {

    @Test
    void chunksSingleChapterContentInOrderWithOffsets() {
        ChapterChunker chunker = new ChapterChunker(40, 6);
        String content = "first paragraph says Qin Yu entered the valley and found the stone gate.\n\n"
                + "second paragraph says Qin Yu opened the gate and saw an ancient array.\n\n"
                + "third paragraph says Qin Yu took the jade slip and remembered the array.";

        List<ChapterChunk> chunks = chunker.chunk(content);

        assertFalse(chunks.isEmpty());
        for (int i = 0; i < chunks.size(); i++) {
            assertEquals(i, chunks.get(i).chunkIndex());
            assertTrue(chunks.get(i).startOffset() >= 0);
            assertTrue(chunks.get(i).endOffset() > chunks.get(i).startOffset());
            assertEquals(chunks.get(i).content(),
                    content.substring(chunks.get(i).startOffset(), chunks.get(i).endOffset()));
        }
    }

    @Test
    void producesDeterministicChunksForSameContent() {
        ChapterChunker chunker = new ChapterChunker(35, 5);
        String content = "A meets B.\n\nB gives a clue.\n\nA enters a hidden room.\n\nA new mechanism appears.";

        List<ChapterChunk> first = chunker.chunk(content);
        List<ChapterChunk> second = chunker.chunk(content);

        assertEquals(first, second);
    }

    @Test
    void returnsEmptyListForBlankContent() {
        ChapterChunker chunker = new ChapterChunker(60, 10);

        assertTrue(chunker.chunk("  \n\n  ").isEmpty());
    }
}
