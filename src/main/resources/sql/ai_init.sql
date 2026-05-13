-- AI 模块数据表
-- 在 novel 库中执行

CREATE TABLE IF NOT EXISTS chapter_summary (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    book_id BIGINT NOT NULL,
    chapter_id BIGINT NOT NULL COMMENT '为哪个章节生成的摘要（使用该章之前的章节内容）',
    summary_text TEXT NOT NULL COMMENT 'AI 生成的前情提要',
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING / COMPLETED / FAILED',
    error_msg TEXT NULL COMMENT '生成失败时的错误信息',
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    UNIQUE KEY uk_book_chapter (book_id, chapter_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS rag_chunk (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    book_id BIGINT NOT NULL,
    chapter_id BIGINT NOT NULL,
    sort_order INT NOT NULL,
    chunk_index INT NOT NULL,
    content TEXT NOT NULL,
    content_hash VARCHAR(64) NOT NULL,
    start_offset INT NOT NULL,
    end_offset INT NOT NULL,
    embedding LONGTEXT NULL,
    embedding_model VARCHAR(80) NULL,
    token_count INT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    UNIQUE KEY uk_chapter_chunk (chapter_id, chunk_index),
    INDEX idx_book_visible (book_id, sort_order),
    INDEX idx_chapter (chapter_id),
    FULLTEXT KEY idx_chunk_content (content)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

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

CREATE TABLE IF NOT EXISTS character_info (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    book_id BIGINT NOT NULL,
    character_name VARCHAR(100) NOT NULL COMMENT '角色名',
    first_chapter_id BIGINT NULL COMMENT '首次出现的章节ID',
    created_at DATETIME NOT NULL,
    UNIQUE KEY uk_book_char (book_id, character_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS character_event (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    character_id BIGINT NOT NULL,
    chapter_id BIGINT NOT NULL,
    book_id BIGINT NOT NULL COMMENT '冗余字段，加速按书查询',
    event_description TEXT NOT NULL COMMENT 'AI 抽取的角色在本章的行为描述',
    created_at DATETIME NOT NULL,
    INDEX idx_book_chapter (book_id, chapter_id),
    INDEX idx_char_chapter (character_id, chapter_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS chapter_embedding (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    chapter_id BIGINT NOT NULL,
    book_id BIGINT NOT NULL COMMENT '冗余字段，加速按书查询',
    embedding LONGTEXT NOT NULL COMMENT 'JSON 格式的浮点数组，如 [0.0123, -0.0456, ...]',
    model VARCHAR(50) NOT NULL COMMENT '使用的 embedding 模型名',
    token_count INT NOT NULL DEFAULT 0 COMMENT '章节 token 数（用于成本估算）',
    created_at DATETIME NOT NULL,
    UNIQUE KEY uk_chapter (chapter_id),
    INDEX idx_book (book_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
