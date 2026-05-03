package com.djs.novel.controller;


import com.djs.novel.dto.Result;
import com.djs.novel.dto.UserDTO;
import com.djs.novel.entity.User;
import com.djs.novel.mapper.UserMapper;
import com.djs.novel.util.UserHolder;
import com.djs.novel.vo.UserVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("api/user")
public class UserController {

    @Autowired
    private UserMapper userMapper;

    /*@GetMapping("/info")
    public Result getUserInfo(@RequestAttribute("userId") Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            return Result.fail("用户不存在");
        }

        UserVO userVO = new UserVO(user.getId(), user.getUsername());
        return Result.ok(userVO);
    }*/

    @GetMapping("/me")
    public Result me() {

        UserDTO user = UserHolder.getUser();
        if (user == null) {
            return Result.fail("未登录");
        }
        return Result.ok(new UserVO(user.getId(), user.getUsername(),user.getImgUrl()));
    }
}
