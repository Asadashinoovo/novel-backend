package com.djs.novel.ai.service;

import com.djs.novel.ai.dto.CharacterSearchRequest;
import com.djs.novel.dto.Result;
import com.djs.novel.entity.BookChapter;

public interface ICharacterService {

    /** 从章节中异步抽取角色和事件 */
    void extractAndStoreCharacters(BookChapter chapter);

    /** 重新抽取（章节更新时） */
    void reExtractForChapter(BookChapter chapter);

    /** 按名称搜索角色 */
    Result searchCharacter(CharacterSearchRequest request);

    /** 获取角色时间线 */
    Result getTimeline(Long characterId, Long maxChapterId);
}
