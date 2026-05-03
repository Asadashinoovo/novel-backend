package com.djs.novel.ai.service;

import com.djs.novel.ai.entity.ChapterEmbedding;

import java.util.List;

public interface IEmbeddingService {

    /** 为章节生成并存储 embedding */
    void generateAndStoreEmbedding(Long chapterId, Long bookId, String content);

    /** 搜索与查询文本最相似的章节 */
    List<ChapterEmbedding> searchSimilar(Long bookId, String queryText, int topK);

    /** 获取某本书的所有 embedding */
    List<ChapterEmbedding> getBookEmbeddings(Long bookId);
}
