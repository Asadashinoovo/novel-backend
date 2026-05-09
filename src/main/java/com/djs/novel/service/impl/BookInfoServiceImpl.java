package com.djs.novel.service.impl;

import com.djs.novel.dto.BookDTO;
import com.djs.novel.dto.Result;

import com.djs.novel.entity.BookInfo;
import com.djs.novel.entity.BookType;
import com.djs.novel.mapper.BookChapterMapper;
import com.djs.novel.mapper.BookInfoMapper;
import com.djs.novel.service.IBookInfoService;
import com.djs.novel.util.UserHolder;
import com.djs.novel.vo.BookVO;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

@Service
public class BookInfoServiceImpl implements IBookInfoService {

    @Autowired
    BookInfoMapper bookInfoMapper;

    @Autowired
    BookChapterMapper bookChapterMapper;



    @Override
    public Result getPublishedBook(Long userId) {
        return Result.ok(bookInfoMapper.getPublishedBookByUserId(userId));
    }

    @Override
    @Transactional
    public Result addBook(BookDTO bookDTO) {
        if (bookDTO == null) {
            return Result.fail("书籍信息不能为空");
        }
        if (bookDTO.getTitle() == null || bookDTO.getTitle().isBlank()) {
            return Result.fail("书名不能为空");
        }
        if (UserHolder.getUser() == null) {
            return Result.fail("未登录");
        }

        bookDTO.setAuthorId(UserHolder.getUser().getId());
        bookDTO.setCreatedAt(LocalDateTime.now());
        bookDTO.setUpdatedAt(LocalDateTime.now());
        bookDTO.setHotCount(0);


        BookInfo bookInfo= new BookInfo();
        BeanUtils.copyProperties(bookDTO,bookInfo);
        bookInfoMapper.insert(bookInfo);

        bookDTO.setId(bookInfo.getId());//把回显的主键给bookdto
        List<BookType> types=bookDTO.getTypes();//给书本添加类型types
        if(types != null && !types.isEmpty()) bookInfoMapper.addTypes(bookDTO.getId(),types);

        return Result.ok(bookDTO.getId());
    }

    @Override
    @Transactional
    public Result updateBook(BookDTO bookDTO) {
        if (bookDTO == null) {
            return Result.fail("书籍信息不能为空");
        }
        if (bookDTO.getId() == null) {
            return Result.fail("ID不能为空");
        }

        if (UserHolder.getUser() == null) {
            return Result.fail("未登录");
        }
        Long userId = UserHolder.getUser().getId();
        List<Long> ids=new LinkedList<>();
        ids.add(bookDTO.getId());
        if(!isOwner(userId,ids)){
            return Result.fail("无权修改此书籍");
        }

        int affected = bookInfoMapper.updateBook(bookDTO);

        if (affected <= 0) {
            return Result.fail("书籍不存在或未修改");
        }

        bookInfoMapper.deleleTypesByBookIds(ids);//删除types
        List<BookType> types=bookDTO.getTypes();
        if(types != null && !types.isEmpty()) bookInfoMapper.addTypes(bookDTO.getId(),types);
        //插入types
        return Result.ok();
    }

    @Override
    @Transactional
    public Result deleteBook(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Result.fail("ID不能为空");
        }
        if (UserHolder.getUser() == null) {
            return Result.fail("未登录");
        }
        Long userId = UserHolder.getUser().getId();
        if(!isOwner(userId,ids)){
            return Result.fail("无权删除此书籍");
        }
        bookChapterMapper.deleteByBookIds(ids);
        int affected = bookInfoMapper.deleteBatchByIds(ids);
        if (affected <= 0) {
            return Result.fail("书籍不存在");
        }
        bookInfoMapper.deleleTypesByBookIds(ids);
        return Result.ok();
    }

    private boolean isOwner(Long userId,List<Long> ids){
        if (userId == null || ids == null || ids.isEmpty() || ids.stream().anyMatch(java.util.Objects::isNull)) {
            return false;
        }
        List<BookInfo> books=bookInfoMapper.getBookByIds(ids);
        if (books == null || books.size() != ids.size()) {
            return false;
        }
        Map<Long, BookInfo> bookById = new HashMap<>();
        for (BookInfo book : books) {
            if (book != null && book.getId() != null) {
                bookById.put(book.getId(), book);
            }
        }
        for (Long id : ids) {
            BookInfo book = bookById.get(id);
            if (book == null || !userId.equals(book.getAuthorId())) {
                return false;
            }
        }
        return true;
    }

    @Override
    public Result getBookById(Long id){
        BookVO book=bookInfoMapper.getBookById(id);
        if (book == null) {
            return Result.fail("书籍不存在");
        }
        book.setTypes(bookInfoMapper.getTypesByBookId(id));

        return Result.ok(book);
    }

    @Override
    public Result getBookTypes(){
        List<BookType> types=bookInfoMapper.getBookTypes();
        return Result.ok(types);
    }

}
