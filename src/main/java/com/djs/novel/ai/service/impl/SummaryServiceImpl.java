package com.djs.novel.ai.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.djs.novel.ai.client.DeepSeekClient;
import com.djs.novel.ai.entity.ChapterSummary;
import com.djs.novel.ai.mapper.ChapterSummaryMapper;
import com.djs.novel.ai.service.ISummaryService;
import com.djs.novel.dto.Result;
import com.djs.novel.entity.BookChapter;
import com.djs.novel.mapper.BookChapterMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class SummaryServiceImpl implements ISummaryService {

    @Autowired
    private DeepSeekClient deepSeekClient;

    @Autowired
    private ChapterSummaryMapper summaryMapper;

    @Autowired
    private BookChapterMapper bookChapterMapper;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    private static final String SUMMARY_CACHE_PREFIX = "ai:summary:";
    private static final int CHAPTER_MAX_CHARS = 2000;

    @Override
    public void generateSummary(BookChapter currentChapter) {
        try {
            ChapterSummary summary = buildSummary(currentChapter, false);
            if (summary != null) {
                summaryMapper.insert(summary);
                cacheSummary(summary);
            }
        } catch (Exception e) {
            log.error("摘要生成异常: chapterId={}, error={}",
                    currentChapter.getId(), e.getMessage(), e);
            saveFailedSummary(currentChapter, e.getMessage());
        }
    }

    @Override
    public void regenerateSummary(BookChapter currentChapter) {
        try {
            // 删除旧摘要
            QueryWrapper<ChapterSummary> wrapper = new QueryWrapper<>();
            wrapper.eq("book_id", currentChapter.getBookId())
                   .eq("chapter_id", currentChapter.getId());
            ChapterSummary existing = summaryMapper.selectOne(wrapper);
            if (existing != null) {
                summaryMapper.deleteById(existing.getId());
            }

            // 重新生成
            ChapterSummary summary = buildSummary(currentChapter, true);
            if (summary != null) {
                summaryMapper.insert(summary);
                cacheSummary(summary);
            }
        } catch (Exception e) {
            log.error("摘要重新生成异常: chapterId={}, error={}",
                    currentChapter.getId(), e.getMessage(), e);
            saveFailedSummary(currentChapter, e.getMessage());
        }
    }

    @Override
    public Result getLatestSummary(Long bookId) {
        ChapterSummary summary = summaryMapper.getLatestSummary(bookId);
        if (summary == null) {
            return Result.fail("暂无前情提要");
        }
        Map<String, Object> data = new HashMap<>();
        data.put("chapterId", summary.getChapterId());
        data.put("summary", summary.getSummaryText());
        return Result.ok(data);
    }

    @Override
    public Result getSummaryUpToChapter(Long bookId, Long chapterId) {
        ChapterSummary summary = summaryMapper.getSummaryUpToChapter(bookId, chapterId);
        if (summary == null) {
            return Result.fail("暂无该章节的前情提要");
        }
        Map<String, Object> data = new HashMap<>();
        data.put("chapterId", summary.getChapterId());
        data.put("summary", summary.getSummaryText());
        return Result.ok(data);
    }

    /**
     * 构建摘要：获取当前章节之前的所有章节，调用 AI 生成总结
     */
    private ChapterSummary buildSummary(BookChapter currentChapter, boolean isRegenerate) {
        // 查询同一本书中 sort_order 小于当前章的所有章节（越章保护核心）
        QueryWrapper<BookChapter> wrapper = new QueryWrapper<>();
        wrapper.eq("book_id", currentChapter.getBookId())
               .lt("sort_order", currentChapter.getSortOrder())
               .orderByAsc("sort_order");

        List<BookChapter> priorChapters = bookChapterMapper.selectList(wrapper);

        if (priorChapters.isEmpty()) {
            // 没有前面的章节，这是第一章，不需要摘要
            ChapterSummary summary = new ChapterSummary();
            summary.setBookId(currentChapter.getBookId());
            summary.setChapterId(currentChapter.getId());
            summary.setSummaryText("这是故事的开篇。");
            summary.setStatus("COMPLETED");
            summary.setCreatedAt(LocalDateTime.now());
            summary.setUpdatedAt(LocalDateTime.now());
            return summary;
        }

        // 构建 prompt：拼接前面章节的内容
        StringBuilder contextBuilder = new StringBuilder();
        for (BookChapter ch : priorChapters) {
            String content = ch.getContent();
            if (content != null && !content.isBlank()) {
                String snippet = content.length() > CHAPTER_MAX_CHARS
                        ? content.substring(0, CHAPTER_MAX_CHARS) : content;
                contextBuilder.append(String.format("--- 第%d章 %s ---\n%s\n\n",
                        ch.getSortOrder(), ch.getTitle(), snippet));
            }
        }

        String systemPrompt = """
                你是一个网文前情提要生成器。根据提供的章节内容，为即将开始的新章节生成一段"前情提要"。
                规则：
                1. 只总结已提供的内容，不要编造任何情节
                2. 用简洁的中文撰写，200-400字
                3. 以"上一章讲到："或"此前："开头
                4. 重点总结主要情节推进和关键转折，忽略日常细节
                5. 如果某个角色有重要行动，提及角色名
                """;

        String userPrompt = String.format("""
                以下是该小说当前章节之前的内容（按章节顺序排列）：

                %s

                请基于以上内容生成前情提要。
                """, contextBuilder.toString());

        String summaryText = deepSeekClient.chatCompletion(systemPrompt, userPrompt);

        if (!StringUtils.hasText(summaryText)) {
            throw new RuntimeException("AI 返回的摘要为空");
        }

        ChapterSummary summary = new ChapterSummary();
        summary.setBookId(currentChapter.getBookId());
        summary.setChapterId(currentChapter.getId());
        summary.setSummaryText(summaryText.trim());
        summary.setStatus("COMPLETED");
        summary.setCreatedAt(LocalDateTime.now());
        summary.setUpdatedAt(LocalDateTime.now());
        return summary;
    }

    private void saveFailedSummary(BookChapter chapter, String errorMsg) {
        try {
            ChapterSummary summary = new ChapterSummary();
            summary.setBookId(chapter.getBookId());
            summary.setChapterId(chapter.getId());
            summary.setSummaryText("");
            summary.setStatus("FAILED");
            summary.setErrorMsg(errorMsg);
            summary.setCreatedAt(LocalDateTime.now());
            summary.setUpdatedAt(LocalDateTime.now());
            summaryMapper.insert(summary);
        } catch (Exception e) {
            log.error("保存失败摘要记录异常", e);
        }
    }

    private void cacheSummary(ChapterSummary summary) {
        try {
            String key = SUMMARY_CACHE_PREFIX + summary.getBookId() + ":" + summary.getChapterId();
            Map<String, String> data = new HashMap<>();
            data.put("summary", summary.getSummaryText());
            data.put("chapterId", String.valueOf(summary.getChapterId()));
            stringRedisTemplate.opsForValue().set(key,
                    data.get("summary"), 30, TimeUnit.MINUTES);
        } catch (Exception e) {
            log.warn("摘要缓存写入失败", e);
        }
    }
}
