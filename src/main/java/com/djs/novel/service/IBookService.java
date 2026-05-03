package com.djs.novel.service;


import com.baomidou.mybatisplus.extension.service.IService;
import com.djs.novel.dto.Result;
import com.djs.novel.entity.BookInfo;


public interface IBookService extends IService<BookInfo> {

    Result getBookByPage(Integer page, Integer pageSize, String keyword);

    Result getBookInfo(Long id);
}
