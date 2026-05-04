package com.djs.novel.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.djs.novel.dto.Result;
import com.djs.novel.entity.BookChapter;
import com.djs.novel.entity.BookInfo;
import com.djs.novel.mapper.BookChapterMapper;
import com.djs.novel.mapper.BookInfoMapper;
import com.djs.novel.service.IChapterService;
import com.djs.novel.util.UserHolder;
import com.djs.novel.vo.ChapterContentVO;
import com.djs.novel.vo.ChapterListVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class ChapterServiceImpl
        extends ServiceImpl<BookChapterMapper, BookChapter>
        implements IChapterService {

    @Autowired
    private BookChapterMapper chapterMapper;

    @Autowired
    BookInfoMapper bookInfoMapper;

    @Override
    @Transactional
    public Result getChapterById(Long bookId, Long id) {
        ChapterContentVO chapter = chapterMapper.getChapterContentById(id, bookId);
        if (chapter == null) {
            return Result.fail("章节不存在");
        }
        chapterMapper.incrReadCount(id);
        return Result.ok(chapter);
    }

    @Override
    public Result getChapterListByBookId(Long bookId) {
        List<ChapterListVO> chapters = chapterMapper.getChapterListByBookId(bookId);
        return Result.ok(chapters);
    }

    @Override
    @Transactional
    public Result addChapter(BookChapter bookChapter) {
        //判断当前用户是否是 这个book的主人
        Long userId = UserHolder.getUser().getId();
        List<Long> ids=new ArrayList<>();
        ids.add(bookChapter.getBookId());

        if(!isOwner(userId,ids)){
            return Result.fail("无权修改此书籍");
        }

        String content=bookChapter.getContent();

        bookChapter.setWordCount(content==null?0 :content.length());
        bookChapter.setCreatedAt(LocalDateTime.now());
        bookChapter.setUpdatedAt(LocalDateTime.now());
        chapterMapper.addChapter(bookChapter);
        return Result.ok();
    }


    @Override
    @Transactional
    public Result updateChapter(BookChapter bookChapter) {
        //判断当前用户是否是 这个book的主人
        Long userId = UserHolder.getUser().getId();
        List<Long> ids=new ArrayList<>();
        ids.add(bookChapter.getBookId());

        if(!isOwner(userId,ids)){
            return Result.fail("无权修改此书籍");
        }

        String content=bookChapter.getContent();
        bookChapter.setWordCount(content==null?0 :content.length());
        chapterMapper.updateChapter(bookChapter);
        return Result.ok();
    }

    @Override
    @Transactional
    public Result deleteChapter(Long bookId, Long id) {
        //判断当前用户是否是 这个book的主人
        Long userId = UserHolder.getUser().getId();
        List<Long> ids=new ArrayList<>();
        ids.add(bookId);

        if(!isOwner(userId,ids)){
            return Result.fail("无权删除此书籍");
        }
        chapterMapper.deleteChapter(bookId, id);
        return Result.ok();
    }

    private boolean isOwner(Long userId,List<Long> ids){
        List<BookInfo> books=bookInfoMapper.getBookByIds(ids);
        for (BookInfo book : books) {
            if(!book.getAuthorId().equals(userId)) return false;
        }
        return true;
    }
}
