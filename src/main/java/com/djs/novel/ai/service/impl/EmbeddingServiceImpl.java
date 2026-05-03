package com.djs.novel.ai.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.djs.novel.ai.entity.ChapterEmbedding;
import com.djs.novel.ai.mapper.ChapterEmbeddingMapper;
import com.djs.novel.ai.service.IEmbeddingService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Embedding 服务当前不再使用向量检索，
 * 改为在 ChatServiceImpl 中使用数据库关键词搜索。
 * 保留此服务以便未来可能需要。
 */
@Service
@Slf4j
public class EmbeddingServiceImpl implements IEmbeddingService {

    @Autowired
    private ChapterEmbeddingMapper embeddingMapper;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public void generateAndStoreEmbedding(Long chapterId, Long bookId, String content) {
        log.info("Embedding 生成已禁用 (DeepSeek 不支持 /v1/embeddings): chapterId={}", chapterId);
    }

    @Override
    public List<ChapterEmbedding> searchSimilar(Long bookId, String queryText, int topK) {
        log.info("Embedding 搜索已禁用，请使用 ChatServiceImpl 中的关键词搜索");
        return List.of();
    }

    @Override
    public List<ChapterEmbedding> getBookEmbeddings(Long bookId) {
        return embeddingMapper.getEmbeddingsByBookId(bookId);
    }
}
