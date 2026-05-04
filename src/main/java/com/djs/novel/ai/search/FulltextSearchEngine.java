package com.djs.novel.ai.search;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.djs.novel.entity.BookChapter;
import com.djs.novel.mapper.BookChapterMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@Slf4j
public class FulltextSearchEngine implements ISearchEngine {

    @Autowired
    private BookChapterMapper bookChapterMapper;

    private static final int SNIPPET_MAX_CHARS = 800;

    /**
     * 使用 MySQL FULLTEXT 索引搜索章节。
     * 如果 FULLTEXT 索引不可用，自动回退到 LIKE 模式。
     * 同时搜索标题和内容，标题匹配对元问题（"第X章讲了什么"）至关重要。
     */
    @Override
    public List<SearchResult> search(SearchQuery query) {
        try {
            return fulltextSearch(query);
        } catch (Exception e) {
            log.warn("FULLTEXT 搜索失败，回退到 LIKE 搜索: {}", e.getMessage());
            return likeSearch(query);
        }
    }

    private List<SearchResult> fulltextSearch(SearchQuery query) {
        String escapedQuery = query.question().replace("'", "''");

        QueryWrapper<BookChapter> wrapper = new QueryWrapper<>();
        wrapper.eq("book_id", query.bookId())
               .apply("MATCH(title, content) AGAINST('" + escapedQuery + "' IN NATURAL LANGUAGE MODE) > 0")
               .orderByDesc("MATCH(title, content) AGAINST('" + escapedQuery + "' IN NATURAL LANGUAGE MODE)")
               .last("LIMIT " + query.topK());

        if (query.maxSortOrder() != null) {
            wrapper.le("sort_order", query.maxSortOrder());
        }

        List<BookChapter> chapters = bookChapterMapper.selectList(wrapper);
        return toSearchResults(chapters);
    }

    /**
     * LIKE 回退搜索：当 FULLTEXT 索引不可用时的降级方案。
     * 使用多窗口 2-gram token 搜索标题和内容。
     */
    private List<SearchResult> likeSearch(SearchQuery query) {
        String text = query.question().replaceAll("\\s+", "").trim();
        if (text.isEmpty()) {
            return List.of();
        }

        String safeText = text.replace("'", "''");

        String sqlCondition;
        if (safeText.length() >= 2) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < safeText.length() - 1; i++) {
                if (sb.length() > 0) sb.append(" OR ");
                String token = safeText.substring(i, i + 2);
                sb.append("(content LIKE '%").append(token)
                  .append("%' OR title LIKE '%").append(token).append("%')");
            }
            sqlCondition = "(" + sb.toString() + ")";
        } else {
            sqlCondition = "(content LIKE '%" + safeText
                         + "%' OR title LIKE '%" + safeText + "%')";
        }

        QueryWrapper<BookChapter> wrapper = new QueryWrapper<>();
        wrapper.eq("book_id", query.bookId())
               .apply(sqlCondition)
               .orderByAsc("sort_order")
               .last("LIMIT " + query.topK());

        if (query.maxSortOrder() != null) {
            wrapper.le("sort_order", query.maxSortOrder());
        }

        List<BookChapter> chapters = bookChapterMapper.selectList(wrapper);
        return toSearchResults(chapters);
    }

    private List<SearchResult> toSearchResults(List<BookChapter> chapters) {
        List<SearchResult> results = new ArrayList<>();
        for (BookChapter ch : chapters) {
            String content = ch.getContent();
            String snippet = "";
            if (content != null && !content.isEmpty()) {
                snippet = content.length() > SNIPPET_MAX_CHARS
                        ? content.substring(0, SNIPPET_MAX_CHARS)
                        : content;
            }
            results.add(new SearchResult(
                    ch.getId(),
                    ch.getSortOrder(),
                    ch.getTitle(),
                    snippet,
                    1.0f));
        }
        return results;
    }
}
