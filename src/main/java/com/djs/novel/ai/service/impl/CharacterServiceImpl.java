package com.djs.novel.ai.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.djs.novel.ai.client.DeepSeekClient;
import com.djs.novel.ai.dto.CharacterSearchRequest;
import com.djs.novel.ai.dto.CharacterTimelineVO;
import com.djs.novel.ai.entity.CharacterEvent;
import com.djs.novel.ai.entity.CharacterInfo;
import com.djs.novel.ai.mapper.CharacterEventMapper;
import com.djs.novel.ai.mapper.CharacterInfoMapper;
import com.djs.novel.ai.service.ICharacterService;
import com.djs.novel.dto.Result;
import com.djs.novel.entity.BookChapter;
import com.djs.novel.mapper.BookChapterMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

@Service
@Slf4j
public class CharacterServiceImpl implements ICharacterService {

    @Autowired
    private DeepSeekClient deepSeekClient;

    @Autowired
    private CharacterInfoMapper characterInfoMapper;

    @Autowired
    private CharacterEventMapper characterEventMapper;

    @Autowired
    private BookChapterMapper bookChapterMapper;

    @Autowired
    private ObjectMapper objectMapper;

    private static final int CHAPTER_MAX_CHARS = 3000;


    @Override
    public void extractAndStoreCharacters(BookChapter chapter) {
        try {
            String content = chapter.getContent();
            if (content == null || content.isBlank()) {
                return;
            }
            String truncated = content.length() > CHAPTER_MAX_CHARS
                    ? content.substring(0, CHAPTER_MAX_CHARS) : content;

            List<CharacterExtraction> extractions = extractCharactersFromContent(
                    chapter.getBookId(), chapter.getTitle(), truncated);

            if (extractions.isEmpty()) {
                log.info("未从章节中抽取到角色: chapterId={}", chapter.getId());
                return;
            }

            for (CharacterExtraction ext : extractions) {
                if (ext.getName() == null || ext.getName().isBlank()) {
                    continue;
                }
                // 查找或创建角色
                CharacterInfo character = findOrCreateCharacter(
                        chapter.getBookId(), ext.getName(), chapter.getId());

                // 保存事件
                if (ext.getAction() != null && !ext.getAction().isBlank()) {
                    CharacterEvent event = new CharacterEvent();
                    event.setCharacterId(character.getId());
                    event.setChapterId(chapter.getId());
                    event.setBookId(chapter.getBookId());
                    event.setEventDescription(ext.getAction());
                    event.setCreatedAt(LocalDateTime.now());
                    characterEventMapper.insert(event);
                }
            }

            log.info("角色抽取完成: chapterId={}, 抽取角色数={}",
                    chapter.getId(), extractions.size());

        } catch (Exception e) {
            log.error("角色抽取异常: chapterId={}, error={}",
                    chapter.getId(), e.getMessage(), e);
        }
    }

    @Override
    public void reExtractForChapter(BookChapter chapter) {
        // 删除旧数据
        characterEventMapper.deleteByChapterId(chapter.getId());
        // 重新抽取
        extractAndStoreCharacters(chapter);
    }

    @Override
    public Result searchCharacter(CharacterSearchRequest request) {
        if (request.getCharacterName() == null || request.getCharacterName().isBlank()) {
            return Result.fail("角色名不能为空");
        }

        // 计算 maxSortOrder（越章保护）
        Integer maxSortOrder = null;
        if (request.getMaxChapterId() != null) {
            BookChapter maxChapter = bookChapterMapper.selectById(request.getMaxChapterId());
            if (maxChapter != null) {
                maxSortOrder = maxChapter.getSortOrder();
            }
        }

        // 搜索角色
        List<CharacterInfo> characters = characterInfoMapper.findByBookIdAndName(
                request.getBookId(), request.getCharacterName());

        if (characters.isEmpty()) {
            return Result.fail("未找到相关角色");
        }

        // 为每个角色构建时间线
        List<Map<String, Object>> results = new ArrayList<>();
        for (CharacterInfo character : characters) {
            List<CharacterTimelineVO> events = characterEventMapper.getEventsByCharacterId(
                    character.getId(), maxSortOrder);

            if (!events.isEmpty()) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("characterId", character.getId());
                item.put("characterName", character.getCharacterName());
                item.put("firstChapterId", character.getFirstChapterId());
                item.put("events", events);
                results.add(item);
            }
        }

        if (results.isEmpty()) {
            return Result.fail("该角色暂无已记录的事迹");
        }

        return Result.ok(results);
    }

    @Override
    public Result getTimeline(Long characterId, Long maxChapterId) {
        CharacterInfo character = characterInfoMapper.selectById(characterId);
        if (character == null) {
            return Result.fail("角色不存在");
        }

        Integer maxSortOrder = null;
        if (maxChapterId != null) {
            BookChapter maxChapter = bookChapterMapper.selectById(maxChapterId);
            if (maxChapter != null) {
                maxSortOrder = maxChapter.getSortOrder();
            }
        }

        List<CharacterTimelineVO> events = characterEventMapper.getEventsByCharacterId(
                characterId, maxSortOrder);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("characterId", character.getId());
        result.put("characterName", character.getCharacterName());
        result.put("events", events);

        return Result.ok(result);
    }

    /**
     * 调用 AI 从章节内容中抽取角色及其行为
     */
    private List<CharacterExtraction> extractCharactersFromContent(
            Long bookId, String chapterTitle, String content) {

        String systemPrompt = """
                你是一个小说角色抽取器。从提供的小说章节内容中识别所有有名字的角色，
                并提取他们在本章中的关键行为或事件。
                规则：
                1. 只返回有名有姓的角色，不要包含"路人"、"士兵"等无名角色
                2. 每个角色的动作描述不超过100字
                3. 如果一个角色在本章没有实际行为，可以省略
                4. 以严格的 JSON 格式返回

                返回 JSON 格式示例：
                {"characters":[{"name":"张三","action":"在云岚宗击败了纳兰嫣然，使用了一招火莲破"},{"name":"李四","action":"在山洞中获得了上古秘籍"}]}
                """;

        String userPrompt = String.format("""
                章节标题：%s
                章节内容：
                %s
                """, chapterTitle, content);

        try {
            // 使用 JSON 输出模式提取
            String jsonStr = deepSeekClient.chatCompletionWithJsonOutput(systemPrompt, userPrompt);
            Map<String, Object> wrapper = objectMapper.readValue(jsonStr,
                    new TypeReference<Map<String, Object>>() {});

            List<Map<String, String>> rawList = (List<Map<String, String>>) wrapper.get("characters");
            if (rawList == null || rawList.isEmpty()) {
                return List.of();
            }

            List<CharacterExtraction> result = new ArrayList<>();
            for (Map<String, String> raw : rawList) {
                CharacterExtraction ext = new CharacterExtraction();
                ext.setName(raw.get("name"));
                ext.setAction(raw.get("action"));
                result.add(ext);
            }
            return result;

        } catch (Exception e) {
            log.error("角色 JSON 解析失败: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * 查找已有角色，不存在则创建
     */
    private CharacterInfo findOrCreateCharacter(Long bookId, String name, Long currentChapterId) {
        QueryWrapper<CharacterInfo> wrapper = new QueryWrapper<>();
        wrapper.eq("book_id", bookId).eq("character_name", name);
        CharacterInfo existing = characterInfoMapper.selectOne(wrapper);

        if (existing != null) {
            return existing;
        }

        CharacterInfo newChar = new CharacterInfo();
        newChar.setBookId(bookId);
        newChar.setCharacterName(name);
        newChar.setFirstChapterId(currentChapterId);
        newChar.setCreatedAt(LocalDateTime.now());
        characterInfoMapper.insert(newChar);
        return newChar;
    }

    @Data
    @NoArgsConstructor
    private static class CharacterExtraction {
        private String name;
        private String action;
    }
}
