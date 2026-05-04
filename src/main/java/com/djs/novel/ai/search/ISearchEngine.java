package com.djs.novel.ai.search;

import java.util.List;

public interface ISearchEngine {
    List<SearchResult> search(SearchQuery query);
}
