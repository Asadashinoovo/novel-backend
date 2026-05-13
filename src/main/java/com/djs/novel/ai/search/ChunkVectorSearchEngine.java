package com.djs.novel.ai.search;

import com.djs.novel.ai.client.DashScopeEmbeddingClient;
import com.djs.novel.ai.entity.RagChunk;
import com.djs.novel.ai.mapper.RagChunkMapper;
import com.djs.novel.ai.vector.RagVectorMatch;
import com.djs.novel.ai.vector.RagVectorStore;
import com.djs.novel.entity.BookChapter;
import com.djs.novel.mapper.BookChapterMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Component
@Slf4j
public class ChunkVectorSearchEngine {

    @Autowired
    private RagChunkMapper ragChunkMapper;

    @Autowired
    private DashScopeEmbeddingClient embeddingClient;

    @Autowired
    private RagVectorStore ragVectorStore;

    @Autowired
    private BookChapterMapper bookChapterMapper;

    public List<ChunkSearchResult> search(ChunkSearchQuery query) {
        try {
            float[] queryVector = embeddingClient.embed(query.question());
            List<RagVectorMatch> matches = ragVectorStore.search(
                    query.bookId(), query.maxSortOrder(), queryVector, query.candidateLimit());
            if (matches.isEmpty()) {
                return List.of();
            }

            List<Long> chunkIds = matches.stream().map(RagVectorMatch::chunkId).toList();
            Map<Long, Float> scoreByChunkId = new HashMap<>();
            for (RagVectorMatch match : matches) {
                scoreByChunkId.put(match.chunkId(), match.score());
            }

            Map<Long, RagChunk> chunkById = new HashMap<>();
            for (RagChunk chunk : ragChunkMapper.selectBatchIds(chunkIds)) {
                chunkById.put(chunk.getId(), chunk);
            }
            Map<Long, String> titles = loadTitles(chunkById.values().stream().toList());

            return chunkIds.stream()
                    .map(chunkById::get)
                    .filter(Objects::nonNull)
                    .filter(chunk -> chunk.getSortOrder() != null
                            && chunk.getSortOrder() <= query.maxSortOrder()
                            && query.bookId().equals(chunk.getBookId()))
                    .map(chunk -> new ChunkSearchResult(
                            chunk.getId(),
                            chunk.getBookId(),
                            chunk.getChapterId(),
                            chunk.getSortOrder(),
                            chunk.getChunkIndex(),
                            titles.getOrDefault(chunk.getChapterId(), ""),
                            chunk.getContent(),
                            scoreByChunkId.getOrDefault(chunk.getId(), 0f),
                            "vector"))
                    .toList();
        } catch (Exception e) {
            log.error("Chunk vector search failed: {}", e.getMessage(), e);
            return List.of();
        }
    }

    private Map<Long, String> loadTitles(List<RagChunk> chunks) {
        Map<Long, String> result = new HashMap<>();
        List<Long> ids = chunks.stream().map(RagChunk::getChapterId).distinct().toList();
        if (ids.isEmpty()) {
            return result;
        }
        for (BookChapter chapter : bookChapterMapper.selectBatchIds(ids)) {
            result.put(chapter.getId(), chapter.getTitle());
        }
        return result;
    }
}
