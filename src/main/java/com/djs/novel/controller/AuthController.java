package com.djs.novel.controller;



import com.djs.novel.dto.LoginRequest;
import com.djs.novel.dto.Result;

import com.djs.novel.service.IAuthService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("api/auth")
@Slf4j
public class AuthController {

    @Autowired
    IAuthService authServiceimpl;

    @PostMapping("/login")
    public Result login(@RequestBody LoginRequest loginRequest) {

        return authServiceimpl.login(loginRequest);
    }

    @PostMapping("/logout")
    public Result logout() {
        return authServiceimpl.logout();

    }

}