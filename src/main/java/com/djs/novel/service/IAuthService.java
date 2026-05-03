package com.djs.novel.service;

import com.djs.novel.dto.LoginRequest;
import com.djs.novel.dto.Result;


public interface IAuthService {
    Result login(LoginRequest loginRequest);

    Result logout();
}
