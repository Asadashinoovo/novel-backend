-- 汇总每个书籍的所有章节字数，更新到 book_info.word_count
UPDATE book_info bi
SET word_count = (
    SELECT COALESCE(SUM(word_count), 0)
    FROM book_chapter
    WHERE book_id = bi.id
);