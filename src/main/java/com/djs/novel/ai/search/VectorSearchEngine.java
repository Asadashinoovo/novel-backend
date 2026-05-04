package com.djs.novel.ai.search;

import com.djs.novel.ai.entity.ChapterEmbedding;
import com.djs.novel.ai.service.IEmbeddingService;
import com.djs.novel.entity.BookChapter;
import com.djs.novel.mapper.BookChapterMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 向量语义检索引擎。
 * 使用 DashScope text-embedding-v4 将文本转为 1024 维向量，
 * 通过余弦相似度找到语义最相关的章节。
 */
@Component
@Slf4j
public class VectorSearchEngine implements ISearchEngine {

    @Autowired
    private IEmbeddingService embeddingService;

    @Autowired
    private BookChapterMapper bookChapterMapper;

    private static final int SNIPPET_MAX_CHARS = 800;

    @Override
    public List<SearchResult> search(SearchQuery query) {
        try {
            // 1. 向量语义搜索
            List<ChapterEmbedding> similar = embeddingService.searchSimilar(
                    query.bookId(), query.question(), query.topK());

            // 2. 加载章节详情，组装 SearchResult
            List<SearchResult> results = new ArrayList<>();
            for (ChapterEmbedding ce : similar) {
                // 越章保护
                if (query.maxSortOrder() != null
                        && ce.getSortOrder() != null
                        && ce.getSortOrder() > query.maxSortOrder()) {
                    continue;
                }

                BookChapter chapter = bookChapterMapper.selectById(ce.getChapterId());
                if (chapter == null) continue;

                String content = chapter.getContent();
                String snippet = "";
                if (content != null && !content.isEmpty()) {
                    snippet = content.length() > SNIPPET_MAX_CHARS
                            ? content.substring(0, SNIPPET_MAX_CHARS) : content;
                }

                results.add(new SearchResult(
                        chapter.getId(),
                        chapter.getSortOrder(),
                        chapter.getTitle(),
                        snippet,
                        1.0f));
            }

            return results;

        } catch (Exception e) {
            log.error("向量搜索失败: {}", e.getMessage(), e);
            return List.of();
        }
    }
}
