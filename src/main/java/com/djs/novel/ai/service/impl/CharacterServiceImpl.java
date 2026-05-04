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
import org.springframework.transaction.annotation.Transactional;

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
    @Transactional
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
    @Transactional
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
            if (jsonStr == null || jsonStr.isBlank()) {
                log.warn("AI 返回空 JSON 字符串");
                return List.of();
            }

            // 尝试从文本中提取 JSON（AI 有时会在 JSON 前后加额外文字）
            String pureJson = jsonStr;
            int braceStart = jsonStr.indexOf('{');
            int braceEnd = jsonStr.lastIndexOf('}');
            if (braceStart >= 0 && braceEnd > braceStart) {
                pureJson = jsonStr.substring(braceStart, braceEnd + 1);
            }

            Map<String, Object> wrapper = objectMapper.readValue(pureJson,
                    new TypeReference<Map<String, Object>>() {});

            Object charactersObj = wrapper.get("characters");
            if (charactersObj == null) {
                return List.of();
            }
            if (!(charactersObj instanceof List)) {
                log.warn("AI 返回的 characters 字段不是数组类型: {}", charactersObj.getClass());
                return List.of();
            }

            List<?> rawList = (List<?>) charactersObj;
            if (rawList.isEmpty()) {
                return List.of();
            }

            List<CharacterExtraction> result = new ArrayList<>();
            for (Object item : rawList) {
                if (!(item instanceof Map)) continue;
                Map<?, ?> raw = (Map<?, ?>) item;
                Object nameObj = raw.get("name");
                Object actionObj = raw.get("action");
                if (nameObj == null) continue;

                CharacterExtraction ext = new CharacterExtraction();
                ext.setName(nameObj.toString());
                ext.setAction(actionObj != null ? actionObj.toString() : "");
                result.add(ext);
            }
            return result;

        } catch (Exception e) {
            log.error("角色 JSON 解析失败: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * 查找已有角色，不存在则创建。
     * 处理并发场景：如果两个章节同时提取到同一新角色，insert 会触发唯一键冲突，
     * 此时重新查询返回另一个线程已插入的记录。
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

        try {
            characterInfoMapper.insert(newChar);
            return newChar;
        } catch (org.springframework.dao.DuplicateKeyException e) {
            // 并发场景：另一个线程已经创建了这个角色，重新查询返回
            log.info("角色已由其他线程创建: bookId={}, name={}", bookId, name);
            return characterInfoMapper.selectOne(wrapper);
        }
    }

    @Data
    @NoArgsConstructor
    private static class CharacterExtraction {
        private String name;
        private String action;
    }
}
