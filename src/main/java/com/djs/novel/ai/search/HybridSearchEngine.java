package com.djs.novel.ai.search;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 混合检索引擎：关键词（FULLTEXT）+ 语义（VECTOR）→ RRF 融合排序。
 */
@Component
public class HybridSearchEngine implements ISearchEngine {

    @Autowired
    private FulltextSearchEngine fulltextSearchEngine;

    @Autowired
    private VectorSearchEngine vectorSearchEngine;

    private static final int RRF_K = 60;
    private static final int EXPAND_K = 10; // 各引擎多取一些再融合

    @Override
    public List<SearchResult> search(SearchQuery query) {
        SearchQuery expandedQuery = new SearchQuery(
                query.bookId(), query.question(), query.maxSortOrder(), EXPAND_K);

        // 并行？这里先串行，避免异步复杂度
        List<SearchResult> fulltextResults = fulltextSearchEngine.search(expandedQuery);
        List<SearchResult> vectorResults = vectorSearchEngine.search(expandedQuery);

        // RRF 融合
        return rrfFusion(fulltextResults, vectorResults, query.topK());
    }

    /**
     * Reciprocal Rank Fusion (RRF):
     * score(d) = Σ 1/(k + rank_i(d))
     * 两个引擎各自排名，融合后重排取 topK
     */
    private List<SearchResult> rrfFusion(List<SearchResult> listA, List<SearchResult> listB, int topK) {
        Map<Long, SearchResult> chapterMap = new LinkedHashMap<>();
        Map<Long, Double> scoreMap = new HashMap<>();

        // 引擎 A 的排名贡献
        for (int i = 0; i < listA.size(); i++) {
            SearchResult sr = listA.get(i);
            chapterMap.putIfAbsent(sr.chapterId(), sr);
            double rrf = 1.0 / (RRF_K + i + 1);
            scoreMap.merge(sr.chapterId(), rrf, Double::sum);
        }

        // 引擎 B 的排名贡献
        for (int i = 0; i < listB.size(); i++) {
            SearchResult sr = listB.get(i);
            chapterMap.putIfAbsent(sr.chapterId(), sr);
            double rrf = 1.0 / (RRF_K + i + 1);
            scoreMap.merge(sr.chapterId(), rrf, Double::sum);
        }

        // 按 RRF 分数排序
        return scoreMap.entrySet().stream()
                .sorted(Map.Entry.<Long, Double>comparingByValue().reversed())
                .limit(topK)
                .map(e -> chapterMap.get(e.getKey()))
                .toList();
    }
}
