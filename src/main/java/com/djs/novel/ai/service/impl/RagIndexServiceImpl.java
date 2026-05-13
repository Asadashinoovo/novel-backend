package com.djs.novel.ai.service.impl;

import com.djs.novel.ai.cache.ICacheLayer;
import com.djs.novel.ai.chunk.ChapterChunk;
import com.djs.novel.ai.chunk.ChapterChunker;
import com.djs.novel.ai.client.DashScopeEmbeddingClient;
import com.djs.novel.ai.entity.RagChunk;
import com.djs.novel.ai.mapper.RagChunkMapper;
import com.djs.novel.ai.service.IRagIndexService;
import com.djs.novel.ai.vector.RagVectorStore;
import com.djs.novel.entity.BookChapter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.DigestUtils;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;

@Service
@Slf4j
public class RagIndexServiceImpl implements IRagIndexService {

    @Autowired
    private RagChunkMapper ragChunkMapper;

    @Autowired
    private DashScopeEmbeddingClient embeddingClient;

    @Autowired
    private RagVectorStore ragVectorStore;

    @Autowired
    private ICacheLayer cacheLayer;

    @Value("${novel.ai.chunk.target-chars:600}")
    private int targetChars;

    @Value("${novel.ai.chunk.overlap-chars:100}")
    private int overlapChars;

    @Value("${dashscope.embedding.model:text-embedding-v4}")
    private String embeddingModel;

    @Override
    @Transactional
    public void rebuildChapterIndex(BookChapter chapter) {
        if (chapter == null || chapter.getId() == null || chapter.getBookId() == null) {
            return;
        }

        try {
            ragVectorStore.deleteByChapterId(chapter.getId());
        } catch (Exception e) {
            log.warn("Failed to delete old vectors before rebuild: chapterId={}, error={}",
                    chapter.getId(), e.getMessage());
        }
        ragChunkMapper.deleteByChapterId(chapter.getId());
        cacheLayer.evictBook(chapter.getBookId());

        String content = chapter.getContent();
        if (content == null || content.isBlank()) {
            log.info("Skip RAG chunk indexing for blank chapter: chapterId={}", chapter.getId());
            return;
        }

        ChapterChunker chunker = new ChapterChunker(targetChars, overlapChars);
        List<ChapterChunk> chunks = chunker.chunk(content);
        LocalDateTime now = LocalDateTime.now();

        for (ChapterChunk chunk : chunks) {
            try {
                float[] vector = embeddingClient.embed(chunk.content());
                RagChunk row = new RagChunk();
                row.setBookId(chapter.getBookId());
                row.setChapterId(chapter.getId());
                row.setSortOrder(chapter.getSortOrder());
                row.setChunkIndex(chunk.chunkIndex());
                row.setContent(chunk.content());
                row.setContentHash(DigestUtils.md5DigestAsHex(chunk.content().getBytes(StandardCharsets.UTF_8)));
                row.setStartOffset(chunk.startOffset());
                row.setEndOffset(chunk.endOffset());
                row.setEmbedding(null);
                row.setEmbeddingModel(embeddingModel);
                row.setTokenCount(chunk.content().length());
                row.setCreatedAt(now);
                row.setUpdatedAt(now);
                ragChunkMapper.insert(row);
                ragVectorStore.upsert(row, vector);
            } catch (Exception e) {
                log.error("Failed to index RAG chunk: chapterId={}, chunkIndex={}, error={}",
                        chapter.getId(), chunk.chunkIndex(), e.getMessage(), e);
            }
        }

        log.info("RAG chunk indexing complete: chapterId={}, chunks={}", chapter.getId(), chunks.size());
    }

    @Override
    @Transactional
    public boolean refreshChapterTextOnly(BookChapter chapter) {
        if (chapter == null || chapter.getId() == null || chapter.getBookId() == null) {
            return false;
        }
        String content = chapter.getContent();
        if (content == null || content.isBlank()) {
            return false;
        }

        ChapterChunker chunker = new ChapterChunker(targetChars, overlapChars);
        List<ChapterChunk> newChunks = chunker.chunk(content);
        List<RagChunk> existingChunks = ragChunkMapper.selectByChapterIdOrderByChunkIndex(chapter.getId());
        if (newChunks.isEmpty() || existingChunks.size() != newChunks.size()) {
            log.info("RAG text-only refresh cannot keep chunk topology: chapterId={}, oldChunks={}, newChunks={}",
                    chapter.getId(), existingChunks.size(), newChunks.size());
            return false;
        }

        LocalDateTime now = LocalDateTime.now();
        for (int i = 0; i < newChunks.size(); i++) {
            ChapterChunk chunk = newChunks.get(i);
            RagChunk existing = existingChunks.get(i);
            if (existing.getChunkIndex() == null || existing.getChunkIndex() != chunk.chunkIndex()) {
                return false;
            }
            RagChunk update = new RagChunk();
            update.setId(existing.getId());
            update.setSortOrder(chapter.getSortOrder());
            update.setContent(chunk.content());
            update.setContentHash(DigestUtils.md5DigestAsHex(chunk.content().getBytes(StandardCharsets.UTF_8)));
            update.setStartOffset(chunk.startOffset());
            update.setEndOffset(chunk.endOffset());
            update.setTokenCount(chunk.content().length());
            update.setUpdatedAt(now);
            ragChunkMapper.updateById(update);
        }

        cacheLayer.evictBook(chapter.getBookId());
        log.info("RAG text-only refresh complete: chapterId={}, chunks={}", chapter.getId(), newChunks.size());
        return true;
    }

    @Override
    @Transactional
    public void deleteChapterIndex(Long bookId, Long chapterId) {
        if (chapterId == null) {
            return;
        }
        ragChunkMapper.deleteByChapterId(chapterId);
        try {
            ragVectorStore.deleteByChapterId(chapterId);
        } catch (Exception e) {
            log.warn("Failed to delete vectors for chapterId={}: {}", chapterId, e.getMessage());
        }
        if (bookId != null) {
            cacheLayer.evictBook(bookId);
        }
    }
}
