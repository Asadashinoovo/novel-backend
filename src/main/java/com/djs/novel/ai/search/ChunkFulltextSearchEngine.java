package com.djs.novel.ai.search;

import com.djs.novel.ai.entity.RagChunk;
import com.djs.novel.ai.mapper.RagChunkMapper;
import com.djs.novel.entity.BookChapter;
import com.djs.novel.mapper.BookChapterMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@Slf4j
public class ChunkFulltextSearchEngine {

    @Autowired
    private RagChunkMapper ragChunkMapper;

    @Autowired
    private BookChapterMapper bookChapterMapper;

    public List<ChunkSearchResult> search(ChunkSearchQuery query) {
        List<RagChunk> chunks;
        try {
            chunks = ragChunkMapper.fulltextVisibleSearch(
                    query.bookId(), query.maxSortOrder(), query.question(), query.candidateLimit());
        } catch (Exception e) {
            log.warn("Chunk FULLTEXT search failed, fallback to LIKE: {}", e.getMessage());
            chunks = ragChunkMapper.likeVisibleSearch(
                    query.bookId(), query.maxSortOrder(), query.question(), query.candidateLimit());
        }
        return toResults(chunks, "fulltext");
    }

    private List<ChunkSearchResult> toResults(List<RagChunk> chunks, String source) {
        Map<Long, String> titleByChapterId = loadTitles(chunks);
        return chunks.stream()
                .map(chunk -> new ChunkSearchResult(
                        chunk.getId(),
                        chunk.getBookId(),
                        chunk.getChapterId(),
                        chunk.getSortOrder(),
                        chunk.getChunkIndex(),
                        titleByChapterId.getOrDefault(chunk.getChapterId(), ""),
                        chunk.getContent(),
                        1.0f,
                        source))
                .toList();
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
