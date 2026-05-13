package com.djs.novel.ai.edit;

import org.springframework.stereotype.Component;
import org.springframework.util.DigestUtils;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

@Component
public class ChapterEditImpactAnalyzer {

    private static final double SEMANTIC_CHANGE_RATIO = 0.12d;
    private static final int TINY_CHANGE_CHARS = 6;

    private static final List<String> SEMANTIC_KEYWORDS = List.of(
            "死亡", "死", "杀", "背叛", "结盟", "获得", "失去", "发现", "真相", "身份",
            "父亲", "母亲", "儿子", "女儿", "师父", "仇人", "敌人", "朋友", "婚",
            "突破", "受伤", "复活", "击败", "进入", "离开", "约定", "契约", "秘密"
    );

    public ChapterEditImpact analyze(String oldTitle, String oldContent, String newTitle, String newContent) {
        String oldText = safe(oldTitle) + "\n" + safe(oldContent);
        String newText = safe(newTitle) + "\n" + safe(newContent);

        if (oldText.equals(newText)) {
            return ChapterEditImpact.noChange();
        }

        String oldNormalized = normalizeForMeaning(oldText);
        String newNormalized = normalizeForMeaning(newText);
        if (oldNormalized.equals(newNormalized)) {
            return ChapterEditImpact.skipAi("only whitespace or punctuation changed");
        }

        ChangedSpan span = changedSpan(oldNormalized, newNormalized);
        String changedText = span.oldPart() + span.newPart();
        if (containsSemanticKeyword(changedText)) {
            return ChapterEditImpact.semantic("semantic keyword changed");
        }

        double ratio = (double) (span.oldPart().length() + span.newPart().length())
                / Math.max(1, Math.max(oldNormalized.length(), newNormalized.length()));
        if (ratio >= SEMANTIC_CHANGE_RATIO) {
            return ChapterEditImpact.semantic("large content change");
        }

        int changedChars = Math.max(span.oldPart().length(), span.newPart().length());
        if (changedChars <= TINY_CHANGE_CHARS) {
            return ChapterEditImpact.ragTextOnly("tiny wording change");
        }

        return ChapterEditImpact.ragTextOnly("small wording change");
    }

    public String rawHash(String title, String content) {
        return md5(safe(title) + "\n" + safe(content));
    }

    public String normalizedHash(String title, String content) {
        return md5(normalizeForMeaning(safe(title) + "\n" + safe(content)));
    }

    public String semanticHash(String title, String content) {
        String normalized = normalizeForMeaning(safe(title) + "\n" + safe(content));
        return md5(stripMinorFunctionWords(normalized));
    }

    private boolean containsSemanticKeyword(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        for (String keyword : SEMANTIC_KEYWORDS) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private ChangedSpan changedSpan(String oldText, String newText) {
        int oldLen = oldText.length();
        int newLen = newText.length();
        int prefix = 0;
        while (prefix < oldLen && prefix < newLen
                && oldText.charAt(prefix) == newText.charAt(prefix)) {
            prefix++;
        }

        int oldSuffix = oldLen - 1;
        int newSuffix = newLen - 1;
        while (oldSuffix >= prefix && newSuffix >= prefix
                && oldText.charAt(oldSuffix) == newText.charAt(newSuffix)) {
            oldSuffix--;
            newSuffix--;
        }

        return new ChangedSpan(
                oldText.substring(prefix, oldSuffix + 1),
                newText.substring(prefix, newSuffix + 1)
        );
    }

    private String normalizeForMeaning(String text) {
        return safe(text)
                .replaceAll("[\\s\\p{P}\\p{S}]+", "")
                .toLowerCase(Locale.ROOT);
    }

    private String stripMinorFunctionWords(String text) {
        return text.replace("的", "")
                .replace("了", "")
                .replace("着", "")
                .replace("啊", "")
                .replace("呢", "");
    }

    private String md5(String text) {
        return DigestUtils.md5DigestAsHex(text.getBytes(StandardCharsets.UTF_8));
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private record ChangedSpan(String oldPart, String newPart) {
    }
}
