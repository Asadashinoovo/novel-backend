package com.djs.novel.ai.mq;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class NoopMqProvider implements IMqProvider {

    @Override
    public void send(MqMessage message) {
        log.debug("MQ 未启用 (noop 模式)，消息被丢弃: taskType={}, bookId={}", message.getTaskType(), message.getBookId());
    }
}
