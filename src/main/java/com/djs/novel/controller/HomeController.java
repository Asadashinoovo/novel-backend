package com.djs.novel.controller;


import com.djs.novel.dto.Result;
import com.djs.novel.service.IBookService;
import com.djs.novel.service.IChapterService;

import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("api/home")
public class HomeController {

    @Autowired
    IBookService bookService;

    @Autowired
    IChapterService chapterService;

    @GetMapping("/book")
    public Result getHomeInfo(@RequestParam(defaultValue = "1") Integer page,
                              @RequestParam(defaultValue = "10") Integer pageSize,
                              @RequestParam(required = false) String keyword) {


        return bookService.getBookByPage(page,pageSize,keyword);
    }

    @GetMapping("/book/{id}")
    public Result getBookInfo(@PathVariable Long id) {
        return bookService.getBookInfo(id);
    }

    @GetMapping("/book/{id}/chapters")
    public Result getBookChapters(@PathVariable Long id) {
        return chapterService.getChapterListByBookId(id);
    }

    @GetMapping("/book/{id}/comments")
    public Result getBookComments(@PathVariable Long id) {
        return Result.ok();
    }

    @GetMapping("/book/{id}/similar")
    public Result getBookSimilar(@PathVariable Long id) {
        return Result.ok();
    }


}
