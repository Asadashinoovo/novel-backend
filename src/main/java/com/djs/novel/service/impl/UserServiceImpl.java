package com.djs.novel.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.djs.novel.entity.User;
import com.djs.novel.mapper.UserMapper;
import com.djs.novel.service.IUserService;
import org.springframework.stereotype.Service;

@Service
public class UserServiceImpl
        extends ServiceImpl<UserMapper, User>
        implements IUserService {
}
