package com.example.kafkademo.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 营销事件消息体。模拟你实际业务里"活动参与"、"批量放款结果"这类需要异步
 * 通知下游处理的事件。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MarketingEvent implements Serializable {

    /** 全局唯一消息ID，用于幂等消费判断（不是Kafka自带的offset，是业务层面的唯一键） */
    private String eventId;

    /** 客户ID，同时也会作为Kafka消息的Key，保证同一客户的消息进同一分区、按顺序消费 */
    private String customerId;

    private String activityId;

    /** 事件类型，比如 ACTIVITY_JOINED（参与活动）/ LOAN_BATCH_RESULT（批量放款结果） */
    private String eventType;

    private String content;

    private LocalDateTime createTime;
}
