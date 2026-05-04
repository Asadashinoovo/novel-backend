package com.djs.novel.ai.config;

import com.djs.novel.ai.search.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
@Slf4j
public class SearchModeConfig {

    @Value("${novel.ai.search-mode:hybrid}")
    private String searchMode;

    @Bean
    @Primary
    public ISearchEngine searchEngine(FulltextSearchEngine fulltextSearchEngine,
                                       VectorSearchEngine vectorSearchEngine,
                                       HybridSearchEngine hybridSearchEngine) {
        SearchMode mode = SearchMode.valueOf(searchMode.toUpperCase());
        log.info("检索模式: {}", mode);
        return switch (mode) {
            case FULLTEXT  -> fulltextSearchEngine;
            case VECTOR    -> vectorSearchEngine;
            case HYBRID    -> hybridSearchEngine;
            case ELASTICSEARCH -> {
                log.warn("ELASTICSEARCH 模式尚未实现，回退到 HYBRID");
                yield hybridSearchEngine;
            }
        };
    }
}
