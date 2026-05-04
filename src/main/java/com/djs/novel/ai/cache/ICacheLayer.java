package com.djs.novel.ai.cache;

import java.util.Optional;

public interface ICacheLayer {
    Optional<String> get(Long bookId, String normalizedQuestion);
    void put(Long bookId, String normalizedQuestion, String answer);
}
