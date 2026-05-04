package com.djs.novel.ai.search;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import jakarta.annotation.PostConstruct;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

@Component
@Slf4j
public class FulltextInitService {

    @Autowired
    private DataSource dataSource;

    @PostConstruct
    public void init() {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {

            // 检查索引是否已存在
            ResultSet rs = stmt.executeQuery(
                    "SELECT COUNT(1) FROM information_schema.statistics " +
                    "WHERE table_schema = DATABASE() AND table_name = 'book_chapter' AND index_name = 'ft_content'");
            rs.next();
            if (rs.getInt(1) > 0) {
                log.info("FULLTEXT 索引 ft_content 已存在");
                return;
            }

            // 创建 FULLTEXT 索引
            stmt.execute("ALTER TABLE book_chapter ADD FULLTEXT INDEX ft_content (title, content) WITH PARSER ngram");
            log.info("FULLTEXT 索引 ft_content 创建成功");

        } catch (Exception e) {
            log.warn("FULLTEXT 索引初始化失败，检索将回退到 LIKE 模式: {}", e.getMessage());
        }
    }
}
