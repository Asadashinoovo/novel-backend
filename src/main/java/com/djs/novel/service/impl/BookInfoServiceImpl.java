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
import java.util.LinkedList;
import java.util.List;

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
        if (bookDTO.getTitle() == null || bookDTO.getTitle().isBlank()) {
            return Result.fail("书名不能为空");
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
        if(!types.isEmpty()) bookInfoMapper.addTypes(bookDTO.getId(),types);

        return Result.ok(bookDTO.getId());
    }

    @Override
    @Transactional
    public Result updateBook(BookDTO bookDTO) {
        if (bookDTO.getId() == null) {
            return Result.fail("ID不能为空");
        }

        Long userId = UserHolder.getUser().getId();
        List<Long> ids=new LinkedList<>();
        ids.add(bookDTO.getId());
        if(!isOwner(userId,ids)){
            return Result.fail("无权修改此书籍");
        }

        bookInfoMapper.updateBook(bookDTO);

        bookInfoMapper.deleleTypesByBookIds(ids);//删除types
        List<BookType> types=bookDTO.getTypes();
        if(!types.isEmpty()) bookInfoMapper.addTypes(bookDTO.getId(),types);
        //插入types
        return Result.ok();
    }

    @Override
    @Transactional
    public Result deleteBook(List<Long> ids) {
        Long userId = UserHolder.getUser().getId();
        if(!isOwner(userId,ids)){
            return Result.fail("无权删除此书籍");
        }
        bookChapterMapper.deleteByBookIds(ids);
        bookInfoMapper.deleteBatchByIds(ids);
        bookInfoMapper.deleleTypesByBookIds(ids);
        return Result.ok();
    }

    private boolean isOwner(Long userId,List<Long> ids){
        List<BookInfo> books=bookInfoMapper.getBookByIds(ids);
        for (BookInfo book : books) {
            if(!book.getAuthorId().equals(userId)) return false;
        }
        return true;
    }

    @Override
    public Result getBookById(Long id){
        BookVO book=bookInfoMapper.getBookById(id);
        book.setTypes(bookInfoMapper.getTypesByBookId(id));

        return Result.ok(book);
    }

    @Override
    public Result getBookTypes(){
        List<BookType> types=bookInfoMapper.getBookTypes();
        return Result.ok(types);
    }

}
