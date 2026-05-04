package com.djs.novel.ai.orchestrator;

import com.djs.novel.ai.dto.ChatRequest;
import com.djs.novel.dto.Result;

public interface AiOrchestrator {
    Result chat(ChatRequest request);
}
