package com.djs.novel.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.djs.novel.entity.BookChapter;
import com.djs.novel.vo.ChapterContentVO;
import com.djs.novel.vo.ChapterListVO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.awt.print.Book;
import java.util.List;

@Mapper
public interface BookChapterMapper extends BaseMapper<BookChapter> {

    List<ChapterListVO> getChapterListByBookId(@Param("bookId") Long bookId);

    ChapterContentVO getChapterContentById(@Param("id") Long id, @Param("bookId") Long bookId);

    int incrReadCount(@Param("id") Long id);

    int deleteByBookIds(@Param("bookIds") List<Long> bookIds);

    int addChapter(@Param("bookChapter") BookChapter bookChapter);

    int updateChapter(@Param("bookChapter") BookChapter bookChapter);

    @Delete("delete from book_chapter where id=#{id} and book_id=#{bookId}")
    int deleteChapter(Long bookId, Long id);

}
