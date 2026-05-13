package com.djs.novel.ai.edit;

public record ChapterEditImpact(
        ChapterEditAction action,
        boolean summaryDownstreamDirty,
        boolean characterDirty,
        boolean ragNeedsRebuild,
        boolean ragTextOnly,
        String reason
) {
    public static ChapterEditImpact noChange() {
        return new ChapterEditImpact(ChapterEditAction.NO_CHANGE, false, false, false, false, "content unchanged");
    }

    public static ChapterEditImpact skipAi(String reason) {
        return new ChapterEditImpact(ChapterEditAction.SKIP_AI, false, false, false, false, reason);
    }

    public static ChapterEditImpact ragTextOnly(String reason) {
        return new ChapterEditImpact(ChapterEditAction.RAG_TEXT_ONLY, false, false, false, true, reason);
    }

    public static ChapterEditImpact semantic(String reason) {
        return new ChapterEditImpact(ChapterEditAction.SEMANTIC_REPROCESS, true, true, true, false, reason);
    }
}
