package com.djs.novel.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.djs.novel.dto.LoginRequest;
import com.djs.novel.dto.Result;
import com.djs.novel.entity.User;
import com.djs.novel.mapper.UserMapper;
import com.djs.novel.service.IAuthService;
import com.djs.novel.util.UserHolder;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class AuthServiceImpl implements IAuthService {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;


    @Override
    public Result login(LoginRequest loginRequest){

        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

        QueryWrapper<User> wrapper = new QueryWrapper<>();
        wrapper.eq("username", loginRequest.getUsername());



        User user = userMapper.selectOne(wrapper);

        if (user == null) {
            return Result.fail("用户名或密码错误");
        }

        if (!encoder.matches(loginRequest.getPassword(), user.getPassword())) {
            return Result.fail("用户名或密码错误");
        }

        Long userId=user.getId();
        String token = UUID.randomUUID().toString();// 生成一份随机的uuid 然后存入redis
        try {

            String userJson = objectMapper.writeValueAsString(user);

            stringRedisTemplate.opsForValue().set("user:id:"+userId,token,60,TimeUnit.MINUTES);
            stringRedisTemplate.opsForValue().set("token:"+token,userJson,60,TimeUnit.MINUTES);
        }catch (JsonProcessingException e){
            log.error("User 序列化失败", e);
            return Result.fail("系统异常");
        }


        Map<String, String> data = new HashMap<>();
        data.put("token", token);

        return Result.ok(data);
    }

    @Override
    public Result logout(){

        Long userId= UserHolder.getUser().getId();

        String token=stringRedisTemplate.opsForValue().get("user:id:"+userId);
        if(token!=null){
            stringRedisTemplate.delete("token:"+token);
            stringRedisTemplate.delete("user:id:"+userId);
        }
        return Result.ok("登出成功");
    }
}
