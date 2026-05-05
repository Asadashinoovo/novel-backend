package com.djs.novel.ai.controller;

import com.djs.novel.ai.dto.ChatRequest;
import com.djs.novel.ai.dto.CharacterSearchRequest;
import com.djs.novel.ai.service.ICharacterService;
import com.djs.novel.ai.orchestrator.AiOrchestrator;
import com.djs.novel.ai.service.ISummaryService;
import com.djs.novel.dto.UserDTO;
import com.djs.novel.dto.Result;
import com.djs.novel.entity.BookInfo;
import com.djs.novel.entity.BookChapter;
import com.djs.novel.mapper.BookInfoMapper;
import com.djs.novel.mapper.BookChapterMapper;
import com.djs.novel.ai.event.ChapterUpdatedEvent;
import com.djs.novel.util.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/ai")
@Slf4j
public class AiController {

    @Autowired
    private ISummaryService summaryService;

    @Autowired
    private ICharacterService characterService;

    @Autowired
    private AiOrchestrator aiOrchestrator;

    @Autowired
    private BookChapterMapper bookChapterMapper;

    @Autowired
    private BookInfoMapper bookInfoMapper;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    /**
     * 获取最新章节的前情提要
     */
    @GetMapping("/summary/{bookId}")
    public Result getLatestSummary(@PathVariable Long bookId) {
        return summaryService.getLatestSummary(bookId);
    }

    /**
     * 获取指定章节之前的前情提要
     */
    @GetMapping("/summary/{bookId}/{chapterId}")
    public Result getSummaryUpToChapter(@PathVariable Long bookId,
                                        @PathVariable Long chapterId) {
        return summaryService.getSummaryUpToChapter(bookId, chapterId);
    }

    /**
     * 搜索角色
     */
    @PostMapping("/character/search")
    public Result searchCharacter(@RequestBody CharacterSearchRequest request) {
        return characterService.searchCharacter(request);
    }

    /**
     * 获取角色时间线
     */
    @GetMapping("/character/{characterId}/timeline")
    public Result getCharacterTimeline(@PathVariable Long characterId,
                                       @RequestParam(required = false) Long maxChapterId) {
        return characterService.getTimeline(characterId, maxChapterId);
    }

    /**
     * AI 对话助手（RAG）
     */
    @PostMapping("/chat")
    public Result chat(@RequestBody ChatRequest request) {
        return aiOrchestrator.chat(request);
    }

    /**
     * 触发 AI 处理：对指定书籍的所有已存在章节进行 AI 处理（摘要+角色抽取）
     */
    @PostMapping("/admin/reprocess/{bookId}")
    public Result reprocessBook(@PathVariable Long bookId) {
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            return Result.fail("未登录");
        }
        BookInfo book = bookInfoMapper.selectById(bookId);
        if (book == null) {
            return Result.fail("书籍不存在");
        }
        if (!user.getId().equals(book.getAuthorId())) {
            return Result.fail("无权重建该书籍的 AI 数据");
        }

        List<BookChapter> chapters = bookChapterMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<BookChapter>()
                        .eq("book_id", bookId)
                        .orderByAsc("sort_order"));

        if (chapters.isEmpty()) {
            return Result.fail("该书没有章节");
        }

        log.info("开始批量 AI 处理: bookId={}, 章节数={}", bookId, chapters.size());

        for (BookChapter chapter : chapters) {
            eventPublisher.publishEvent(new ChapterUpdatedEvent(this, chapter));
        }

        return Result.ok(Map.of(
                "message", "已触发 AI 处理",
                "chapterCount", chapters.size(),
                "note", "摘要和角色数据将在后台异步生成，请稍后查看"
        ));
    }
}
