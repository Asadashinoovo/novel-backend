package com.djs.novel.ai.search;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ChunkHybridSearchEngineTest {

    @Test
    void rrfDeduplicatesByChunkIdAndKeepsBestFusedResults() {
        List<ChunkSearchResult> fulltext = List.of(
                result(1L, "fulltext"),
                result(2L, "fulltext"),
                result(3L, "fulltext")
        );
        List<ChunkSearchResult> vector = List.of(
                result(2L, "vector"),
                result(4L, "vector"),
                result(1L, "vector")
        );

        List<ChunkSearchResult> fused = ChunkHybridSearchEngine.fuseForTest(fulltext, vector, 3);

        assertEquals(3, fused.size());
        assertEquals(2L, fused.get(0).chunkId());
        assertEquals("hybrid", fused.get(0).source());
    }

    private ChunkSearchResult result(Long chunkId, String source) {
        return new ChunkSearchResult(chunkId, 1L, 10L + chunkId, 10, 0,
                "chapter ten", "content " + chunkId, 1.0f, source);
    }
}
