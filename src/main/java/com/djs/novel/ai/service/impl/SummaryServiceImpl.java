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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class SummaryServiceImpl implements ISummaryService {

    @Autowired
    private DeepSeekClient deepSeekClient;

    @Autowired
    private ChapterSummaryMapper summaryMapper;

    @Autowired
    private BookChapterMapper bookChapterMapper;

    private static final int CHAPTER_SNIPPET_CHARS = 800;
    // 渐进模式：前几章全量拼接，后面的章节用前序摘要 + 最近 N 章正文
    private static final int FULL_MODE_MAX_CHAPTERS = 5;
    private static final int PROGRESSIVE_RECENT_CHAPTERS = 3;

    @Override
    @Transactional
    public void generateSummary(BookChapter currentChapter) {
        try {
            ChapterSummary summary = buildSummary(currentChapter, false);
            if (summary != null) {
                summaryMapper.insert(summary);
            }
        } catch (Exception e) {
            log.error("摘要生成异常: chapterId={}, error={}",
                    currentChapter.getId(), e.getMessage(), e);
            saveFailedSummary(currentChapter, e.getMessage());
        }
    }

    @Override
    @Transactional
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
     * 构建摘要。
     * 前几章用全量拼接（内容少），后面的章节用渐进模式：
     * 用前一章的摘要 + 最近几章正文，避免 prompt 随章节数膨胀。
     */
    private ChapterSummary buildSummary(BookChapter currentChapter, boolean isRegenerate) {
        // 只取最近的前置章节（SQL 层加 LIMIT，避免全表扫描）
        List<BookChapter> priorChapters = bookChapterMapper.selectList(
                new QueryWrapper<BookChapter>()
                        .eq("book_id", currentChapter.getBookId())
                        .lt("sort_order", currentChapter.getSortOrder())
                        .orderByDesc("sort_order")
                        .last("LIMIT " + FULL_MODE_MAX_CHAPTERS));
        Collections.reverse(priorChapters);

        if (priorChapters.isEmpty()) {
            return firstChapterSummary(currentChapter);
        }

        String summaryText;
        if (priorChapters.size() <= FULL_MODE_MAX_CHAPTERS) {
            // 全量模式：章节少，直接用正文拼接
            summaryText = generateFromScratch(priorChapters);
        } else {
            // 渐进模式：用前序摘要 + 最近几章正文
            ChapterSummary previousSummary = getPreviousSummary(currentChapter);
            summaryText = generateProgressive(priorChapters, previousSummary);
        }

        if (!StringUtils.hasText(summaryText)) {
            throw new RuntimeException("AI 返回的摘要为空");
        }

        return buildSummaryEntity(currentChapter, summaryText);
    }

    /**
     * 全量模式：直接用所有前置章节正文生成摘要
     */
    private String generateFromScratch(List<BookChapter> priorChapters) {
        StringBuilder ctx = new StringBuilder();
        for (int i = 0; i < priorChapters.size(); i++) {
            appendChapterSnippet(ctx, i + 1, priorChapters.get(i));
        }
        return deepSeekClient.chatCompletion(SUMMARY_SYSTEM_PROMPT,
                String.format("以下是该小说当前章节之前的内容（按章节顺序排列）：\n\n%s\n\n请基于以上内容生成前情提要。", ctx));
    }

    /**
     * 渐进模式：前序摘要 + 最近几章的正文细节
     */
    private String generateProgressive(List<BookChapter> allPrior, ChapterSummary previousSummary) {
        StringBuilder ctx = new StringBuilder();

        // 前序摘要（已有总结，压缩了前面所有章节的信息）
        if (previousSummary != null && StringUtils.hasText(previousSummary.getSummaryText())) {
            ctx.append("【前面章节的摘要】\n");
            ctx.append(previousSummary.getSummaryText()).append("\n\n");
        }

        // 最近几章的正文细节（让摘要跟最新剧情衔接）
        int startIdx = Math.max(0, allPrior.size() - PROGRESSIVE_RECENT_CHAPTERS);
        int chapterNum = allPrior.size() - (allPrior.size() - startIdx) + 1;
        ctx.append("【最近章节详情】\n");
        for (int i = startIdx; i < allPrior.size(); i++) {
            appendChapterSnippet(ctx, chapterNum++, allPrior.get(i));
        }

        String progressivePrompt = SUMMARY_SYSTEM_PROMPT + "\n"
                + "重要：提供的【前面章节的摘要】已经覆盖了更早的剧情，你只需要基于它和【最近章节详情】生成完整的前情提要。";

        return deepSeekClient.chatCompletion(progressivePrompt,
                String.format("请基于以下信息生成前情提要：\n\n%s\n\n", ctx));
    }

    private void appendChapterSnippet(StringBuilder sb, int chapterNum, BookChapter ch) {
        String content = ch.getContent();
        if (content != null && !content.isBlank()) {
            String snippet = content.length() > CHAPTER_SNIPPET_CHARS
                    ? content.substring(0, CHAPTER_SNIPPET_CHARS) : content;
            sb.append(String.format("--- 第%d章 %s ---\n%s\n\n", chapterNum, ch.getTitle(), snippet));
        }
    }

    private ChapterSummary firstChapterSummary(BookChapter currentChapter) {
        return buildSummaryEntity(currentChapter, "这是故事的开篇。");
    }

    private ChapterSummary buildSummaryEntity(BookChapter chapter, String text) {
        ChapterSummary summary = new ChapterSummary();
        summary.setBookId(chapter.getBookId());
        summary.setChapterId(chapter.getId());
        summary.setSummaryText(text);
        summary.setStatus("COMPLETED");
        summary.setCreatedAt(LocalDateTime.now());
        summary.setUpdatedAt(LocalDateTime.now());
        return summary;
    }

    /**
     * 获取当前章节的前一个摘要（用于渐进模式）
     */
    private ChapterSummary getPreviousSummary(BookChapter currentChapter) {
        return summaryMapper.getSummaryUpToSortOrder(
                currentChapter.getBookId(), currentChapter.getSortOrder());
    }

    private static final String SUMMARY_SYSTEM_PROMPT = """
            你是一个网文前情提要生成器。根据提供的章节内容，为即将开始的新章节生成一段"前情提要"。
            规则：
            1. 只总结已提供的内容，不要编造任何情节
            2. 用简洁的中文撰写，200-400字
            3. 以"上一章讲到："或"此前："开头
            4. 重点总结主要情节推进和关键转折，忽略日常细节
            5. 如果某个角色有重要行动，提及角色名
            """;

    @Transactional
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

}
