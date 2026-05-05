package com.djs.novel.ai.cache;

import java.util.Optional;

public interface ICacheLayer {
    Optional<String> get(Long bookId, Long maxChapterId, String normalizedQuestion);
    void put(Long bookId, Long maxChapterId, String normalizedQuestion, String answer);
}
