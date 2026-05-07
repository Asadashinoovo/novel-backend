package com.djs.novel.ai.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

@Configuration
public class AiConfig {

    @Bean
    public RestTemplate deepseekRestTemplate(
            @Value("${deepseek.api.connect-timeout:10}") int connectTimeoutSeconds,
            @Value("${deepseek.api.timeout:60}") int timeoutSeconds) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(connectTimeoutSeconds));
        factory.setReadTimeout(Duration.ofSeconds(timeoutSeconds));
        return new RestTemplate(factory);
    }
}
