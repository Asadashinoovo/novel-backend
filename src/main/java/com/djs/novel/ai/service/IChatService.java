package com.djs.novel.ai.service;

import com.djs.novel.ai.dto.ChatRequest;
import com.djs.novel.dto.Result;

public interface IChatService {

    Result chat(ChatRequest request);
}
