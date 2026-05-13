package com.djs.novel.ai.rerank;

import com.djs.novel.ai.search.ChunkSearchResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertSame;

class NoopRerankServiceTest {

    @Test
    void returnsSameCandidateOrder() {
        NoopRerankService service = new NoopRerankService();
        List<ChunkSearchResult> candidates = List.of(
                new ChunkSearchResult(1L, 10L, 100L, 10, 0, "chapter one", "Qin Yu enters the valley", 0.5f, "hybrid"),
                new ChunkSearchResult(2L, 10L, 100L, 10, 1, "chapter one", "Qin Yu finds the stone gate", 0.4f, "hybrid")
        );

        List<ChunkSearchResult> result = service.rerank("what did Qin Yu find", candidates);

        assertSame(candidates, result);
    }
}
