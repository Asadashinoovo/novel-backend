package com.djs.novel.ai.rerank;

import com.djs.novel.ai.search.ChunkSearchResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Slf4j
@ConditionalOnProperty(name = "novel.ai.rerank-enabled", havingValue = "false", matchIfMissing = true)
public class NoopRerankService implements IRerankService {

    @Override
    public List<ChunkSearchResult> rerank(String question, List<ChunkSearchResult> candidates) {
        log.debug("Noop rerank: candidates={}", candidates == null ? 0 : candidates.size());
        return candidates;
    }
}
