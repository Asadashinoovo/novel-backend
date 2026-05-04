package com.djs.novel.ai.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.djs.novel.ai.client.DashScopeEmbeddingClient;
import com.djs.novel.ai.entity.ChapterEmbedding;
import com.djs.novel.ai.mapper.ChapterEmbeddingMapper;
import com.djs.novel.ai.service.IEmbeddingService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

/**
 * 使用阿里云 DashScope text-embedding-v3 进行文本向量化。
 * 向量以 JSON 数组形式存入 MySQL chapter_embedding 表。
 * 检索时加载书籍所有向量，Java 内存计算余弦相似度。
 */
@Service
@Slf4j
public class EmbeddingServiceImpl implements IEmbeddingService {

    @Autowired
    private ChapterEmbeddingMapper embeddingMapper;

    @Autowired
    private DashScopeEmbeddingClient embeddingClient;

    @Autowired
    private ObjectMapper objectMapper;

    @Override
    @Transactional
    public void generateAndStoreEmbedding(Long chapterId, Long bookId, String content) {
        try {
            // 截取前 8000 字符（v3 模型最大 8192 tokens）
            String truncated = content.length() > 8000 ? content.substring(0, 8000) : content;

            float[] vector = embeddingClient.embed(truncated);
            if (vector == null || vector.length == 0) {
                log.error("向量化返回空: chapterId={}", chapterId);
                return;
            }

            // 序列化为 JSON 数组
            String json = objectMapper.writeValueAsString(vector);

            // 查重：如果已有同章节 embedding，更新
            QueryWrapper<ChapterEmbedding> wrapper = new QueryWrapper<>();
            wrapper.eq("chapter_id", chapterId);
            ChapterEmbedding existing = embeddingMapper.selectOne(wrapper);

            if (existing != null) {
                existing.setEmbedding(json);
                existing.setModel("text-embedding-v3");
                existing.setTokenCount(truncated.length());
                existing.setCreatedAt(LocalDateTime.now());
                embeddingMapper.updateById(existing);
            } else {
                ChapterEmbedding ce = new ChapterEmbedding();
                ce.setChapterId(chapterId);
                ce.setBookId(bookId);
                ce.setEmbedding(json);
                ce.setModel("text-embedding-v3");
                ce.setTokenCount(truncated.length());
                ce.setCreatedAt(LocalDateTime.now());
                embeddingMapper.insert(ce);
            }

            log.info("向量化完成: chapterId={}, dims={}", chapterId, vector.length);

        } catch (Exception e) {
            log.error("向量化失败: chapterId={}, error={}", chapterId, e.getMessage(), e);
        }
    }

    @Override
    public List<ChapterEmbedding> searchSimilar(Long bookId, String queryText, int topK) {
        try {
            // 1. 向量化查询
            float[] queryVector = embeddingClient.embed(queryText);
            if (queryVector == null) {
                return List.of();
            }

            // 2. 加载该书所有章节向量
            List<ChapterEmbedding> allEmbeddings = getBookEmbeddings(bookId);
            if (allEmbeddings.isEmpty()) {
                return List.of();
            }

            // 3. 计算余弦相似度
            record ScoredEmbedding(ChapterEmbedding embedding, double score) {}

            List<ScoredEmbedding> scored = new ArrayList<>();
            for (ChapterEmbedding ce : allEmbeddings) {
                float[] chapterVector = parseVector(ce.getEmbedding());
                if (chapterVector == null) continue;
                double similarity = cosineSimilarity(queryVector, chapterVector);
                scored.add(new ScoredEmbedding(ce, similarity));
            }

            // 4. 排序取 topK
            scored.sort((a, b) -> Double.compare(b.score(), a.score()));

            List<ChapterEmbedding> result = new ArrayList<>();
            for (int i = 0; i < Math.min(topK, scored.size()); i++) {
                result.add(scored.get(i).embedding());
            }
            return result;

        } catch (Exception e) {
            log.error("向量搜索失败: {}", e.getMessage(), e);
            return List.of();
        }
    }

    @Override
    public List<ChapterEmbedding> getBookEmbeddings(Long bookId) {
        return embeddingMapper.getEmbeddingsByBookId(bookId);
    }

    /**
     * 余弦相似度: dot(A,B) / (|A| * |B|)
     */
    private double cosineSimilarity(float[] a, float[] b) {
        double dot = 0, normA = 0, normB = 0;
        for (int i = 0; i < a.length; i++) {
            dot += (double) a[i] * b[i];
            normA += (double) a[i] * a[i];
            normB += (double) b[i] * b[i];
        }
        if (normA == 0 || normB == 0) return 0;
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    private float[] parseVector(String json) {
        try {
            double[] doubles = objectMapper.readValue(json, double[].class);
            float[] floats = new float[doubles.length];
            for (int i = 0; i < doubles.length; i++) {
                floats[i] = (float) doubles[i];
            }
            return floats;
        } catch (JsonProcessingException e) {
            log.error("向量 JSON 解析失败: {}", e.getMessage());
            return null;
        }
    }
}
