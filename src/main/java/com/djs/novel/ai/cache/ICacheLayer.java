package com.djs.novel.ai.cache;

import com.djs.novel.ai.dto.ChatResponse;

import java.util.Optional;

public interface ICacheLayer {
    Optional<ChatResponse> get(Long bookId, Long maxChapterId, String normalizedQuestion);
    void put(Long bookId, Long maxChapterId, String normalizedQuestion, ChatResponse response);
    void evictBook(Long bookId);
}
