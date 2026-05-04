package com.djs.novel.ai.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

/**
 * 阿里云 DashScope 文本向量化客户端。
 * 使用 OpenAI 兼容接口，model=text-embedding-v4，1024 维。
 */
@Component
@Slf4j
public class DashScopeEmbeddingClient {

    @Value("${dashscope.api.key}")
    private String apiKey;

    @Value("${dashscope.embedding.model:text-embedding-v3}")
    private String model;

    @Value("${dashscope.embedding.dimensions:1024}")
    private int dimensions;

    private static final String BASE_URL = "https://dashscope.aliyuncs.com/compatible-mode/v1";

    private final RestTemplate restTemplate;

    public DashScopeEmbeddingClient() {
        this.restTemplate = new RestTemplate();
    }

    /**
     * 将单条文本转为向量。
     */
    public float[] embed(String text) {
        float[][] result = embedBatch(List.of(text));
        return (result != null && result.length > 0) ? result[0] : null;
    }

    /**
     * 批量文本转向量，每批最多 10 条。
     */
    public float[][] embedBatch(List<String> texts) {
        try {
            Map<String, Object> requestBody = Map.of(
                    "model", model,
                    "input", texts,
                    "dimensions", dimensions,
                    "encoding_format", "float"
            );

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);

            ResponseEntity<Map> response = restTemplate.postForEntity(
                    BASE_URL + "/embeddings", request, Map.class);

            Map<String, Object> body = response.getBody();
            if (body == null) {
                log.error("DashScope Embedding API 返回空 body");
                return null;
            }

            List<Map<String, Object>> data = (List<Map<String, Object>>) body.get("data");
            if (data == null || data.isEmpty()) {
                log.error("DashScope Embedding API 返回空 data");
                return null;
            }

            float[][] result = new float[data.size()][];
            for (int i = 0; i < data.size(); i++) {
                List<Double> embedding = (List<Double>) data.get(i).get("embedding");
                if (embedding == null) continue;
                result[i] = new float[embedding.size()];
                for (int j = 0; j < embedding.size(); j++) {
                    result[i][j] = embedding.get(j).floatValue();
                }
            }
            return result;

        } catch (Exception e) {
            log.error("DashScope Embedding API 调用失败: {}", e.getMessage(), e);
            throw new RuntimeException("向量化服务调用失败: " + e.getMessage(), e);
        }
    }
}
