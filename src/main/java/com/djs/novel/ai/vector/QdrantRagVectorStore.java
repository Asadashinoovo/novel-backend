package com.djs.novel.ai.vector;

import com.djs.novel.ai.entity.RagChunk;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
@ConditionalOnProperty(name = "novel.ai.vector-store", havingValue = "qdrant", matchIfMissing = true)
public class QdrantRagVectorStore implements RagVectorStore {

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper;
    private final String baseUrl;
    private final String collectionName;
    private final int vectorSize;
    private final String distance;
    private volatile boolean collectionReady = false;

    public QdrantRagVectorStore(
            ObjectMapper objectMapper,
            @Value("${novel.ai.qdrant.base-url:http://localhost:6333}") String baseUrl,
            @Value("${novel.ai.qdrant.collection-name:novel_rag_chunks}") String collectionName,
            @Value("${novel.ai.qdrant.vector-size:1024}") int vectorSize,
            @Value("${novel.ai.qdrant.distance:Cosine}") String distance) {
        this.objectMapper = objectMapper;
        this.baseUrl = trimTrailingSlash(baseUrl);
        this.collectionName = collectionName;
        this.vectorSize = vectorSize;
        this.distance = distance;
    }

    @Override
    public void upsert(RagChunk chunk, float[] vector) {
        if (chunk == null || chunk.getId() == null || vector == null || vector.length == 0) {
            return;
        }
        ensureCollection();
        Map<String, Object> point = new LinkedHashMap<>();
        point.put("id", chunk.getId());
        point.put("vector", vector);
        point.put("payload", Map.of(
                "bookId", chunk.getBookId(),
                "chapterId", chunk.getChapterId(),
                "sortOrder", chunk.getSortOrder(),
                "chunkIndex", chunk.getChunkIndex()
        ));
        post("/collections/" + collectionName + "/points?wait=true", Map.of("points", List.of(point)));
    }

    @Override
    public void deleteByChapterId(Long chapterId) {
        if (chapterId == null) {
            return;
        }
        ensureCollection();
        Map<String, Object> body = Map.of(
                "filter", Map.of(
                        "must", List.of(match("chapterId", chapterId))
                )
        );
        post("/collections/" + collectionName + "/points/delete?wait=true", body);
    }

    @Override
    public List<RagVectorMatch> search(Long bookId, Integer maxSortOrder, float[] queryVector, int limit) {
        if (bookId == null || maxSortOrder == null || queryVector == null || queryVector.length == 0) {
            return List.of();
        }
        ensureCollection();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("vector", queryVector);
        body.put("limit", limit);
        body.put("with_payload", true);
        body.put("filter", Map.of(
                "must", List.of(
                        match("bookId", bookId),
                        rangeLte("sortOrder", maxSortOrder)
                )
        ));

        try {
            String response = post("/collections/" + collectionName + "/points/search", body);
            JsonNode result = objectMapper.readTree(response).path("result");
            List<RagVectorMatch> matches = new ArrayList<>();
            if (result.isArray()) {
                for (JsonNode item : result) {
                    matches.add(new RagVectorMatch(item.path("id").asLong(), (float) item.path("score").asDouble()));
                }
            }
            return matches;
        } catch (Exception e) {
            log.error("Qdrant vector search failed: {}", e.getMessage(), e);
            return List.of();
        }
    }

    private void ensureCollection() {
        if (collectionReady) {
            return;
        }
        if (collectionExists()) {
            collectionReady = true;
            return;
        }
        Map<String, Object> body = Map.of(
                "vectors", Map.of(
                        "size", vectorSize,
                        "distance", distance
                )
        );
        put("/collections/" + collectionName, body);
        collectionReady = true;
    }

    private boolean collectionExists() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/collections/" + collectionName))
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                return true;
            }
            if (response.statusCode() == 404) {
                return false;
            }
            throw new IllegalStateException("Qdrant collection check returned "
                    + response.statusCode() + ": " + response.body());
        } catch (IOException e) {
            throw new IllegalStateException("Cannot connect to Qdrant at " + baseUrl, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Qdrant collection check interrupted", e);
        }
    }

    private Map<String, Object> match(String key, Object value) {
        return Map.of("key", key, "match", Map.of("value", value));
    }

    private Map<String, Object> rangeLte(String key, Number value) {
        return Map.of("key", key, "range", Map.of("lte", value));
    }

    private void put(String path, Object body) {
        request("PUT", path, body);
    }

    private String post(String path, Object body) {
        return request("POST", path, body);
    }

    private String request(String method, String path, Object body) {
        try {
            String json = objectMapper.writeValueAsString(body);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + path))
                    .header("Content-Type", "application/json")
                    .method(method, HttpRequest.BodyPublishers.ofString(json))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                return response.body();
            }
            throw new IllegalStateException("Qdrant " + method + " " + path
                    + " returned " + response.statusCode() + ": " + response.body());
        } catch (IOException e) {
            throw new IllegalStateException("Cannot connect to Qdrant at " + baseUrl, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Qdrant request interrupted", e);
        }
    }

    private String trimTrailingSlash(String value) {
        if (value == null || value.isBlank()) {
            return "http://localhost:6333";
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
