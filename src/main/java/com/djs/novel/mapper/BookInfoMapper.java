package com.djs.novel.mapper;


import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.djs.novel.dto.BookDTO;
import com.djs.novel.entity.BookInfo;
import com.djs.novel.entity.BookType;
import com.djs.novel.vo.BookVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;


@Mapper
public interface BookInfoMapper extends BaseMapper<BookInfo> {


    List<BookVO> getBookInfoByPage(@Param("offset") Integer offset,
                                   @Param("pageSize") Integer pageSize,
                                   @Param("keyword") String keyword);

    BookVO getBookById(Long id);

    List<BookInfo> getBookByIds(List<Long> ids);

    List<BookType> getTypesByBookId(Long id);



    @Select("select * from book_info where author_id=#{userId}")
    List<BookVO> getPublishedBookByUserId(Long userId);

    int updateBook(BookDTO bookDTO);

    int deleteBatchByIds(@Param("ids") List<Long> ids);


    int deleleTypesByBookIds(@Param("ids") List<Long> ids);

    @Select("select id,type_name from book_type ")
    List<BookType> getBookTypes();

    int addTypes(@Param("bookId") Long bookId, @Param("types") List<BookType> types);

    int deleteTypes(@Param("bookId") Long bookId, @Param("types") List<BookType> types);
}
