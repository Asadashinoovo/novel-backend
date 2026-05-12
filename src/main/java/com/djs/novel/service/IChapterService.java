package com.djs.novel.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.djs.novel.dto.Result;
import com.djs.novel.entity.BookChapter;
import org.apache.ibatis.annotations.Delete;

public interface IChapterService extends IService<BookChapter> {

    Result getChapterById(Long bookId,Long id);

    Result getChapterListByBookId(Long bookId);

    Result addChapter(BookChapter bookChapter, Long afterChapterId);

    Result updateChapter(BookChapter bookChapter);

    Result deleteChapter(Long bookId, Long id);
}
