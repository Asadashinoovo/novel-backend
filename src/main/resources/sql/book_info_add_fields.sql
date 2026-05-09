ALTER TABLE book_info
    ADD COLUMN is_finished TINYINT DEFAULT 0 COMMENT '是否完结：0-连载中，1-已完结',
    ADD COLUMN word_count  BIGINT  DEFAULT 0 COMMENT '总字数',
    ADD COLUMN status      TINYINT DEFAULT 1 COMMENT '状态：0-待审核，1-已发布，2-下架';