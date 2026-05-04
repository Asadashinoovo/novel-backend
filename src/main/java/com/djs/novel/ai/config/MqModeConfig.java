package com.djs.novel.ai.config;

import com.djs.novel.ai.mq.IMqProvider;
import com.djs.novel.ai.mq.MqMode;
import com.djs.novel.ai.mq.NoopMqProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
@Slf4j
public class MqModeConfig {

    @Value("${novel.ai.mq-mode:noop}")
    private String mqMode;

    @Bean
    @Primary
    public IMqProvider mqProvider(NoopMqProvider noopMqProvider) {
        MqMode mode = MqMode.valueOf(mqMode.toUpperCase());
        log.info("MQ 模式: {}", mode);
        return switch (mode) {
            case NOOP -> noopMqProvider;
            case ROCKETMQ, RABBITMQ -> {
                log.warn("{} 模式尚未实现，回退到 NOOP", mode);
                yield noopMqProvider;
            }
        };
    }
}
