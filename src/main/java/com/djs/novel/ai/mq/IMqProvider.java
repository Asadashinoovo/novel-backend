package com.djs.novel.ai.mq;

public interface IMqProvider {
    void send(MqMessage message);
}
