package com.djs.novel.ai.rerank;

import com.djs.novel.ai.search.ChunkSearchResult;

import java.util.List;

public interface IRerankService {
    List<ChunkSearchResult> rerank(String question, List<ChunkSearchResult> candidates);
}
