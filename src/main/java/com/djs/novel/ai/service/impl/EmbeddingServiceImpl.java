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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

/**
 * 使用阿里云 DashScope text-embedding-v4 进行文本向量化。
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

    @Value("${dashscope.embedding.model:text-embedding-v4}")
    private String embeddingModel = "text-embedding-v4";

    /** 每章最多取几个段落做向量化 */
    private static final int MAX_CHUNKS = 4;
    /** 每个段落最大字符数 */
    private static final int MAX_CHUNK_CHARS = 500;

    @Override
    @Transactional
    public void generateAndStoreEmbedding(Long chapterId, Long bookId, String content) {
        try {
            // 智能分段：取章节的关键段落，而不是傻截前 8000 字
            List<String> chunks = chunkContent(content, MAX_CHUNKS, MAX_CHUNK_CHARS);
            if (chunks.isEmpty()) {
                log.warn("章节无有效文本可向量化: chapterId={}", chapterId);
                return;
            }

            // 拼接段落作为向量化文本（用换行分隔保持语义独立）
            String text = String.join("\n\n", chunks);
            int tokenEstimate = text.length(); // 粗略估算: 1 字符 ≈ 1 token (中文)

            float[] vector = embeddingClient.embed(text);
            if (vector == null || vector.length == 0) {
                log.error("向量化返回空: chapterId={}", chapterId);
                return;
            }

            String json = objectMapper.writeValueAsString(vector);

            QueryWrapper<ChapterEmbedding> wrapper = new QueryWrapper<>();
            wrapper.eq("chapter_id", chapterId);
            ChapterEmbedding existing = embeddingMapper.selectOne(wrapper);

            if (existing != null) {
                existing.setEmbedding(json);
                existing.setModel(embeddingModel);
                existing.setTokenCount(tokenEstimate);
                existing.setCreatedAt(LocalDateTime.now());
                embeddingMapper.updateById(existing);
            } else {
                ChapterEmbedding ce = new ChapterEmbedding();
                ce.setChapterId(chapterId);
                ce.setBookId(bookId);
                ce.setEmbedding(json);
                ce.setModel(embeddingModel);
                ce.setTokenCount(tokenEstimate);
                ce.setCreatedAt(LocalDateTime.now());
                embeddingMapper.insert(ce);
            }

            log.info("向量化完成: chapterId={}, chunks={}, tokens≈{}",
                    chapterId, chunks.size(), tokenEstimate);

        } catch (Exception e) {
            log.error("向量化失败: chapterId={}, error={}", chapterId, e.getMessage(), e);
        }
    }

    static List<String> chunkContent(String content, int maxChunks, int maxCharsPerChunk) {
        if (content == null || content.isBlank()) {
            return List.of();
        }

        // 按段落切分（双换行或单换行）
        String[] rawParagraphs = content.split("\\n\\s*\\n|\\n");
        List<String> paragraphs = new java.util.ArrayList<>();
        for (String p : rawParagraphs) {
            String trimmed = p.trim();
            if (trimmed.length() < 20) continue; // 跳过太短的（空行、分隔符等）
            paragraphs.add(trimmed);
        }

        if (paragraphs.isEmpty()) {
            return List.of();
        }

        List<String> selected = new java.util.ArrayList<>();

        // 1. 取开头段（通常是章节引子/背景介绍，检索价值高）
        selected.add(truncateChunk(paragraphs.get(0), maxCharsPerChunk));

        // 2. 取中间的关键段落（找长度适中的段落，代表剧情主体）
        if (paragraphs.size() >= 3 && maxChunks >= 2) {
            int mid = paragraphs.size() / 2;
            selected.add(truncateChunk(paragraphs.get(mid), maxCharsPerChunk));
        }

        // 3. 如果还有额度，取第一章和中间之间的一段
        if (paragraphs.size() >= 5 && maxChunks >= 3) {
            int q1 = paragraphs.size() / 4;
            selected.add(truncateChunk(paragraphs.get(q1), maxCharsPerChunk));
        }

        // 4. 如果还有额度，取结尾段（通常是章节收尾/悬念，语义信息丰富）
        if (paragraphs.size() >= 2 && maxChunks >= 4) {
            String truncated = truncateChunk(paragraphs.get(paragraphs.size() - 1), maxCharsPerChunk);
            if (!selected.contains(truncated)) {
                selected.add(truncated);
            }
        }

        return selected;
    }

    private static String truncateChunk(String text, int maxChars) {
        if (text.length() <= maxChars) return text;
        // 尽量在句号处截断，避免截断词语
        int cut = text.lastIndexOf('。', maxChars);
        if (cut > maxChars / 2) {
            return text.substring(0, cut + 1);
        }
        return text.substring(0, maxChars);
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
        if (a == null || b == null || a.length != b.length) {
            return 0;
        }
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
