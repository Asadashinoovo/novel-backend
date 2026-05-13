package com.djs.novel.ai.search;

import java.util.List;

public interface IChunkSearchEngine {
    List<ChunkSearchResult> search(ChunkSearchQuery query);
}
