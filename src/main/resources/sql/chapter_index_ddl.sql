-- 章节知识图谱索引表
-- 存储章节中抽取的实体（角色、地点、事件、概念、物品）
CREATE TABLE IF NOT EXISTS chapter_index (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    chapter_id BIGINT NOT NULL,
    book_id BIGINT NOT NULL,
    entity_type VARCHAR(30) NOT NULL COMMENT 'CHARACTER / LOCATION / EVENT / CONCEPT / ITEM',
    entity_name VARCHAR(200) NOT NULL,
    ref_id BIGINT NULL COMMENT '关联ID，如关联 character_info.id',
    created_at DATETIME NOT NULL,
    INDEX idx_book_entity (book_id, entity_type, entity_name(100))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- AI 答案缓存表 (Redis 为一级缓存，MySQL 为二级持久化)
CREATE TABLE IF NOT EXISTS ai_cache (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    book_id BIGINT NOT NULL,
    question_hash VARCHAR(64) NOT NULL COMMENT '问题归一化后的 MD5 hash',
    question_text VARCHAR(500) NOT NULL,
    answer_text TEXT NOT NULL,
    hit_count INT DEFAULT 0,
    created_at DATETIME NOT NULL,
    UNIQUE KEY uk_hash (question_hash)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
