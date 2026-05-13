package com.djs.novel.ai.chunk;

import java.util.ArrayList;
import java.util.List;

public class ChapterChunker {

    private final int targetChars;
    private final int overlapChars;

    public ChapterChunker(int targetChars, int overlapChars) {
        if (targetChars < 20) {
            throw new IllegalArgumentException("targetChars must be at least 20");
        }
        if (overlapChars < 0 || overlapChars >= targetChars) {
            throw new IllegalArgumentException("overlapChars must be >= 0 and < targetChars");
        }
        this.targetChars = targetChars;
        this.overlapChars = overlapChars;
    }

    public List<ChapterChunk> chunk(String content) {
        if (content == null || content.isBlank()) {
            return List.of();
        }

        List<Paragraph> paragraphs = splitParagraphs(content);
        if (paragraphs.isEmpty()) {
            return List.of();
        }

        List<ChapterChunk> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int chunkStart = -1;
        int chunkEnd = -1;

        for (Paragraph paragraph : paragraphs) {
            if (current.isEmpty()) {
                chunkStart = paragraph.startOffset();
                chunkEnd = paragraph.endOffset();
                current.append(paragraph.text());
                continue;
            }

            int projectedLength = current.length() + 2 + paragraph.text().length();
            if (projectedLength > targetChars) {
                addChunk(chunks, content, chunkStart, chunkEnd);
                int overlapStart = calculateOverlapStart(content, chunkStart, chunkEnd);
                current.setLength(0);
                chunkStart = overlapStart;
                chunkEnd = paragraph.endOffset();
                current.append(content, overlapStart, chunkEnd);
            } else {
                current.append("\n\n").append(paragraph.text());
                chunkEnd = paragraph.endOffset();
            }
        }

        if (!current.isEmpty() && chunkStart >= 0) {
            addChunk(chunks, content, chunkStart, chunkEnd);
        }

        return chunks;
    }

    private List<Paragraph> splitParagraphs(String content) {
        List<Paragraph> paragraphs = new ArrayList<>();
        int length = content.length();
        int cursor = 0;

        while (cursor < length) {
            while (cursor < length && Character.isWhitespace(content.charAt(cursor))) {
                cursor++;
            }
            int start = cursor;
            while (cursor < length) {
                char ch = content.charAt(cursor);
                if (ch == '\n' || ch == '\r') {
                    break;
                }
                cursor++;
            }
            int end = cursor;
            String text = content.substring(start, end).trim();
            if (!text.isEmpty() && text.length() >= 2) {
                paragraphs.add(new Paragraph(text, start, end));
            }
            while (cursor < length && Character.isWhitespace(content.charAt(cursor))) {
                cursor++;
            }
        }

        return paragraphs;
    }

    private void addChunk(List<ChapterChunk> chunks, String original, int startOffset, int endOffset) {
        String exactText = original.substring(startOffset, endOffset);
        chunks.add(new ChapterChunk(chunks.size(), exactText, startOffset, endOffset));
    }

    private int calculateOverlapStart(String content, int chunkStart, int chunkEnd) {
        if (overlapChars == 0) {
            return chunkEnd;
        }
        int desired = Math.max(chunkStart, chunkEnd - overlapChars);
        while (desired > chunkStart && !Character.isWhitespace(content.charAt(desired - 1))) {
            desired--;
        }
        return Math.max(chunkStart, desired);
    }

    private record Paragraph(String text, int startOffset, int endOffset) {
    }
}
