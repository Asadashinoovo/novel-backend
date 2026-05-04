package com.djs.novel.ai.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

@Component
@Slf4j
public class DeepSeekClient {

    @Value("${deepseek.api.key}")
    private String apiKey;

    @Value("${deepseek.api.base-url}")
    private String baseUrl;

    @Value("${deepseek.api.chat-model}")
    private String chatModel;

    @Autowired
    @Qualifier("deepseekRestTemplate")
    private RestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * 调用 DeepSeek chat completion，返回文本。
     * deepseek-v4-pro 是推理模型，会先产生 reasoning_content 再产生 content，
     * 因此需要足够大的 max_tokens。
     */
    public String chatCompletion(String systemPrompt, String userPrompt) {
        try {
            Map<String, Object> requestBody = Map.of(
                    "model", chatModel,
                    "messages", List.of(
                            Map.of("role", "system", "content", systemPrompt),
                            Map.of("role", "user", "content", userPrompt)
                    ),
                    "max_tokens", 2048
            );

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);

            ResponseEntity<Map> response = restTemplate.postForEntity(
                    baseUrl + "/v1/chat/completions", request, Map.class);

            Map<String, Object> body = response.getBody();
            if (body == null) {
                log.error("DeepSeek API 返回空 body");
                return null;
            }

            List<Map<String, Object>> choices = (List<Map<String, Object>>) body.get("choices");
            if (choices == null || choices.isEmpty()) {
                log.error("DeepSeek API 返回空 choices");
                return null;
            }

            Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
            String content = (String) message.get("content");

            // 推理模型可能在 content 为空时把结果放在 reasoning_content 中
            if (content == null || content.isBlank()) {
                String reasoning = (String) message.get("reasoning_content");
                if (reasoning != null && !reasoning.isBlank()) {
                    // 取 reasoning 的最后部分作为有效回答
                    log.info("使用 reasoning_content 作为输出 (长度: {})", reasoning.length());
                    return reasoning;
                }
                log.warn("DeepSeek 返回空 content 且无 reasoning_content");
                return null;
            }

            return content;

        } catch (Exception e) {
            log.error("DeepSeek API 调用失败: {}", e.getMessage(), e);
            throw new RuntimeException("AI 服务调用失败: " + e.getMessage(), e);
        }
    }

    /**
     * 调用 DeepSeek 并尝试提取 JSON 结构化结果。
     * 推理模型不支持 response_format: json_object，所以改用 prompt 引导 JSON 输出。
     */
    public String chatCompletionWithJsonOutput(String systemPrompt, String userPrompt) {
        // 在 system prompt 中强调返回 JSON
        String jsonSystemPrompt = systemPrompt + "\n\n重要：你必须只返回一个合法的 JSON 对象，不要包含任何 markdown 标记、代码块或额外文字。";
        String raw = chatCompletion(jsonSystemPrompt, userPrompt);
        if (raw == null) {
            return null;
        }
        // 尝试提取 JSON 块
        String trimmed = raw.trim();
        if (trimmed.startsWith("```json")) {
            trimmed = trimmed.substring(7);
        }
        if (trimmed.startsWith("```")) {
            trimmed = trimmed.substring(3);
        }
        if (trimmed.endsWith("```")) {
            trimmed = trimmed.substring(0, trimmed.length() - 3);
        }
        return trimmed.trim();
    }
}
