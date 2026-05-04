package com.djs.novel.ai.mq;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MqMessage implements Serializable {

    /** 任务类型: CHAT / SUMMARY / CHARACTER / INDEX */
    private String taskType;

    private Long bookId;

    private Long chapterId;

    private Long maxChapterId;

    private String question;

    /** 用户 ID，异步任务完成后回调 */
    private String userId;
}
