package com.djs.novel.ai.cache;

import com.djs.novel.ai.dto.ChatResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Component
@Slf4j
public class RedisCacheLayer implements ICacheLayer {

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Value("${novel.ai.cache-enabled:true}")
    private boolean cacheEnabled;

    @Value("${novel.ai.cache-ttl-hours:24}")
    private int cacheTtlHours;

    private static final String KEY_PREFIX = "ai:cache:";

    @Override
    public Optional<ChatResponse> get(Long bookId, Long maxChapterId, String normalizedQuestion) {
        if (!cacheEnabled) {
            return Optional.empty();
        }
        try {
            String key = buildKey(bookId, maxChapterId, normalizedQuestion);
            String cached = redisTemplate.opsForValue().get(key);
            if (cached != null) {
                log.info("缓存命中: bookId={}, maxChapterId={}", bookId, maxChapterId);
                return Optional.of(readCachedResponse(cached));
            }
        } catch (Exception e) {
            log.warn("Redis 缓存读取失败: {}", e.getMessage());
        }
        return Optional.empty();
    }

    @Override
    public void put(Long bookId, Long maxChapterId, String normalizedQuestion, ChatResponse response) {
        if (!cacheEnabled) {
            return;
        }
        try {
            String key = buildKey(bookId, maxChapterId, normalizedQuestion);
            String value = objectMapper.writeValueAsString(response);
            redisTemplate.opsForValue().set(key, value, cacheTtlHours, TimeUnit.HOURS);
            log.info("缓存写入: bookId={}, maxChapterId={}, answerLength={}",
                    bookId, maxChapterId, response.getAnswer() != null ? response.getAnswer().length() : 0);
        } catch (Exception e) {
            log.warn("Redis 缓存写入失败: {}", e.getMessage());
        }
    }

    @Override
    public void evictBook(Long bookId) {
        if (!cacheEnabled || bookId == null) {
            return;
        }
        try {
            Set<String> keys = redisTemplate.keys(KEY_PREFIX + bookId + ":*");
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
                log.info("清理书籍 AI 缓存: bookId={}, keys={}", bookId, keys.size());
            }
        } catch (Exception e) {
            log.warn("Redis 缓存清理失败: bookId={}, error={}", bookId, e.getMessage());
        }
    }

    private ChatResponse readCachedResponse(String cached) throws Exception {
        try {
            return objectMapper.readValue(cached, ChatResponse.class);
        } catch (Exception ignored) {
            return new ChatResponse(cached, java.util.List.of());
        }
    }

    private String buildKey(Long bookId, Long maxChapterId, String normalizedQuestion) {
        String hash = hash(normalizedQuestion);
        return KEY_PREFIX + bookId + ":" + (maxChapterId != null ? maxChapterId : "0") + ":" + hash;
    }

    /**
     * 问题归一化后计算 MD5 哈希。
     * 归一化：去所有空白、标点、英文小写。
     */
    static String hash(String question) {
        String normalized = question.replaceAll("[\\s\\p{Punct}]+", "")
                .trim()
                .toLowerCase();
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(normalized.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            // MD5 always available, fallback
            return Integer.toHexString(normalized.hashCode());
        }
    }
}
