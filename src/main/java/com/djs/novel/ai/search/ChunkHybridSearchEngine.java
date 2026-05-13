package com.djs.novel.ai.search;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class ChunkHybridSearchEngine implements IChunkSearchEngine {

    private static final int RRF_K = 60;

    @Autowired
    private ChunkFulltextSearchEngine fulltextSearchEngine;

    @Autowired
    private ChunkVectorSearchEngine vectorSearchEngine;

    @Override
    public List<ChunkSearchResult> search(ChunkSearchQuery query) {
        List<ChunkSearchResult> fulltext = fulltextSearchEngine.search(query);
        List<ChunkSearchResult> vector = vectorSearchEngine.search(query);
        return rrfFusion(fulltext, vector, query.candidateLimit());
    }

    static List<ChunkSearchResult> fuseForTest(List<ChunkSearchResult> fulltext,
                                               List<ChunkSearchResult> vector,
                                               int limit) {
        return rrfFusion(fulltext, vector, limit);
    }

    private static List<ChunkSearchResult> rrfFusion(List<ChunkSearchResult> fulltext,
                                                    List<ChunkSearchResult> vector,
                                                    int limit) {
        Map<Long, ChunkSearchResult> chunkById = new LinkedHashMap<>();
        Map<Long, Double> scoreById = new HashMap<>();

        addScores(fulltext, chunkById, scoreById);
        addScores(vector, chunkById, scoreById);

        return scoreById.entrySet().stream()
                .sorted(Map.Entry.<Long, Double>comparingByValue(Comparator.reverseOrder()))
                .limit(limit)
                .map(entry -> chunkById.get(entry.getKey()).withScoreAndSource(entry.getValue().floatValue(), "hybrid"))
                .toList();
    }

    private static void addScores(List<ChunkSearchResult> results,
                                  Map<Long, ChunkSearchResult> chunkById,
                                  Map<Long, Double> scoreById) {
        for (int i = 0; i < results.size(); i++) {
            ChunkSearchResult result = results.get(i);
            chunkById.putIfAbsent(result.chunkId(), result);
            double rrf = 1.0d / (RRF_K + i + 1);
            scoreById.merge(result.chunkId(), rrf, Double::sum);
        }
    }
}
