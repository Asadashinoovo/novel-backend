package com.djs.novel.service;

import com.djs.novel.dto.BookDTO;
import com.djs.novel.dto.Result;
import com.djs.novel.entity.BookInfo;

import java.util.List;

public interface IBookInfoService {
    Result getPublishedBook(Long userId);
    Result addBook(BookDTO bookDTO);
    Result updateBook(BookDTO bookDTO);
    Result deleteBook(List<Long> ids);
    Result getBookById(Long id);

    Result getBookTypes();
}
