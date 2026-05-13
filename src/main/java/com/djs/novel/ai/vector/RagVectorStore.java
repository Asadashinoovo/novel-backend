package com.djs.novel.ai.vector;

import com.djs.novel.ai.entity.RagChunk;

import java.util.List;

public interface RagVectorStore {

    void upsert(RagChunk chunk, float[] vector);

    void deleteByChapterId(Long chapterId);

    List<RagVectorMatch> search(Long bookId, Integer maxSortOrder, float[] queryVector, int limit);
}
