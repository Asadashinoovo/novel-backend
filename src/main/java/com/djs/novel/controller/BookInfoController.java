package com.djs.novel.controller;

import com.djs.novel.dto.BookDTO;
import com.djs.novel.dto.LoginRequest;
import com.djs.novel.dto.Result;
import com.djs.novel.entity.BookInfo;
import com.djs.novel.service.IBookInfoService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("api/bookinfo")
@Slf4j
public class BookInfoController {

    @Autowired
    IBookInfoService bookInfoService;

    @GetMapping("/getmybook/{id}")
    public Result getPublishedBook(@PathVariable Long id) {
        return bookInfoService.getPublishedBook(id);
    }

    @GetMapping("/getbookbyid/{id}")
    public Result getBookById(@PathVariable Long id){
        return bookInfoService.getBookById(id);
    }

    @PostMapping("/add")
    public Result addNewBook(@RequestBody BookDTO bookDTO) {
        return bookInfoService.addBook(bookDTO);
    }

    @PostMapping("/update")
    public Result updateBook(@RequestBody BookDTO bookDTO) {
        return bookInfoService.updateBook(bookDTO);
    }

    @PostMapping("/delete")
    public Result deleteBook(@RequestBody List<Long> ids) {
        return bookInfoService.deleteBook(ids);
    }

    @GetMapping("/types")
    public Result getTypes(){
        return bookInfoService.getBookTypes();
    }


}
