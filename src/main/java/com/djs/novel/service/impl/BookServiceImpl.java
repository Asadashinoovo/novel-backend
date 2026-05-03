package com.djs.novel.service.impl;


import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.djs.novel.dto.Result;
import com.djs.novel.entity.BookInfo;
import com.djs.novel.entity.BookType;
import com.djs.novel.mapper.BookInfoMapper;
import com.djs.novel.service.IBookService;
import com.djs.novel.vo.BookVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;


@Service
@Slf4j
public class BookServiceImpl
        extends ServiceImpl<BookInfoMapper,BookInfo>
        implements IBookService {

    @Autowired
    BookInfoMapper bookInfoMapper;

    @Override
    public Result getBookByPage(Integer page, Integer pageSize, String keyword) {

        int offset=(page-1)*pageSize;

        List<BookVO> books=bookInfoMapper.getBookInfoByPage(offset,pageSize,keyword);

        return Result.ok(books);
    }

    @Override
    public Result getBookInfo(Long id){

        BookVO book=bookInfoMapper.getBookById(id);

        book.setTypes(bookInfoMapper.getTypesByBookId(id));

        return Result.ok(book);
    }
}
