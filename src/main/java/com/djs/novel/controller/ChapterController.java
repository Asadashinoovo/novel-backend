package com.djs.novel.controller;

import com.djs.novel.dto.Result;
import com.djs.novel.entity.BookChapter;
import com.djs.novel.service.IChapterService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("api/chapter")
public class ChapterController {

    @Autowired
    private IChapterService chapterService;

    @GetMapping("/get/{bookId}/{id}")
    public Result getChapter(@PathVariable Long bookId, @PathVariable Long id) {
        return chapterService.getChapterById(bookId, id);
    }

    @PostMapping("/add")
    public Result addChapter(@RequestBody BookChapter bookChapter,
                             @RequestParam(required = false) Long afterChapterId) {
        return chapterService.addChapter(bookChapter, afterChapterId);
    }


    @PostMapping("/update")
    public Result updateChapter(@RequestBody BookChapter bookChapter) {
        return chapterService.updateChapter(bookChapter);
    }

    @PostMapping("/delete/{bookId}/{id}")
    public Result deleteChapter(@PathVariable Long bookId, @PathVariable Long id) {
        return chapterService.deleteChapter(bookId, id);
    }

}
