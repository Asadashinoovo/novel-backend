package com.djs.novel.ai.knowledge;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.djs.novel.ai.entity.ChapterIndex;
import com.djs.novel.ai.entity.CharacterEvent;
import com.djs.novel.ai.entity.CharacterInfo;
import com.djs.novel.ai.mapper.ChapterIndexMapper;
import com.djs.novel.ai.mapper.CharacterEventMapper;
import com.djs.novel.ai.mapper.CharacterInfoMapper;
import com.djs.novel.entity.BookChapter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Component
@Slf4j
public class ChapterIndexBuilder {

    @Autowired
    private ChapterIndexMapper chapterIndexMapper;

    @Autowired
    private CharacterEventMapper characterEventMapper;

    @Autowired
    private CharacterInfoMapper characterInfoMapper;

    /**
     * 从已抽取的角色事件中构建知识图谱索引。
     * 在 CharacterServiceImpl.extractAndStoreCharacters 之后调用。
     */
    @Transactional
    public void build(BookChapter chapter) {
        try {
            // 删除旧索引
            chapterIndexMapper.deleteByChapterId(chapter.getId());

            // 获取本章的角色事件
            List<CharacterEvent> events = characterEventMapper.selectList(
                    new QueryWrapper<CharacterEvent>()
                            .eq("chapter_id", chapter.getId()));

            for (CharacterEvent event : events) {
                CharacterInfo character = characterInfoMapper.selectById(event.getCharacterId());
                if (character == null) {
                    continue;
                }

                ChapterIndex index = new ChapterIndex();
                index.setChapterId(chapter.getId());
                index.setBookId(chapter.getBookId());
                index.setEntityType("CHARACTER");
                index.setEntityName(character.getCharacterName());
                index.setRefId(character.getId());
                index.setCreatedAt(LocalDateTime.now());
                chapterIndexMapper.insert(index);
            }

            if (!events.isEmpty()) {
                log.info("知识图谱索引构建完成: chapterId={}, 实体数={}", chapter.getId(), events.size());
            }

        } catch (Exception e) {
            log.error("知识图谱索引构建失败: chapterId={}, error={}", chapter.getId(), e.getMessage());
        }
    }
}
