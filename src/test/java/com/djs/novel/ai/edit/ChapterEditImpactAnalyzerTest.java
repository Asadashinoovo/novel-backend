package com.djs.novel.ai.edit;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChapterEditImpactAnalyzerTest {

    private final ChapterEditImpactAnalyzer analyzer = new ChapterEditImpactAnalyzer();

    @Test
    void skipsAiWorkForWhitespaceAndPunctuationOnlyChanges() {
        ChapterEditImpact impact = analyzer.analyze(
                "第一章",
                "秦羽走进山谷，发现石门。\n\n他停下脚步。",
                "第一章",
                "秦羽走进山谷, 发现石门。\n\n\n他停下脚步。");

        assertEquals(ChapterEditAction.SKIP_AI, impact.action());
        assertFalse(impact.summaryDownstreamDirty());
        assertFalse(impact.characterDirty());
        assertFalse(impact.ragNeedsRebuild());
    }

    @Test
    void usesTextOnlyRagRefreshForTinyTypoChanges() {
        ChapterEditImpact impact = analyzer.analyze(
                "第一章",
                "秦羽走进山谷，发现石门。他听见远处传来脚步声。",
                "第一章",
                "秦羽走进山谷，发现石门。他听见远处传来脚步音。");

        assertEquals(ChapterEditAction.RAG_TEXT_ONLY, impact.action());
        assertFalse(impact.summaryDownstreamDirty());
        assertFalse(impact.characterDirty());
        assertTrue(impact.ragTextOnly());
    }

    @Test
    void reprocessesSemanticDataWhenPlotFactsChange() {
        ChapterEditImpact impact = analyzer.analyze(
                "第一章",
                "秦羽进入山洞，获得玉简，并与林月结盟。",
                "第一章",
                "秦羽进入山洞，失去玉简，并背叛林月。");

        assertEquals(ChapterEditAction.SEMANTIC_REPROCESS, impact.action());
        assertTrue(impact.summaryDownstreamDirty());
        assertTrue(impact.characterDirty());
        assertTrue(impact.ragNeedsRebuild());
    }

    @Test
    void reprocessesSemanticDataWhenChangeRatioIsLarge() {
        ChapterEditImpact impact = analyzer.analyze(
                "第一章",
                "秦羽进入山谷，发现石门，随后返回客栈休息。",
                "第一章",
                "林月在皇城遭遇追杀，秦羽赶到后救下她，并得知王族秘令。");

        assertEquals(ChapterEditAction.SEMANTIC_REPROCESS, impact.action());
        assertTrue(impact.summaryDownstreamDirty());
        assertTrue(impact.ragNeedsRebuild());
    }
}
