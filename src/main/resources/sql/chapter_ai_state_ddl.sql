-- Tracks the last AI-processing fingerprint for each chapter.
-- Execute once in the novel database before enabling smart chapter-update handling.

CREATE TABLE IF NOT EXISTS chapter_ai_state (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    book_id BIGINT NOT NULL,
    chapter_id BIGINT NOT NULL,
    raw_hash VARCHAR(64) NOT NULL,
    normalized_hash VARCHAR(64) NOT NULL,
    semantic_hash VARCHAR(64) NOT NULL,
    last_action VARCHAR(40) NOT NULL,
    last_reason VARCHAR(255) NULL,
    processed_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    UNIQUE KEY uk_chapter_ai_state (chapter_id),
    INDEX idx_book_chapter_ai_state (book_id, chapter_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
