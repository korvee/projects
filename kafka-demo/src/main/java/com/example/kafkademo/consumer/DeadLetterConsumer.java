package com.example.kafkademo.consumer;

import com.example.kafkademo.entity.MarketingEvent;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import static com.example.kafkademo.config.KafkaTopicConfig.MARKETING_EVENTS_DLT;

/**
 * 死信队列监控消费者：专门消费"多次重试仍然失败"的消息。
 *
 * 真实生产环境里，这里通常会做：
 *   1. 把失败消息连同失败原因写入一张"failed_messages"表，方便人工排查
 *   2. 触发告警（发消息到企业微信/钉钉群），通知开发及时介入
 *   3. 提供一个后台管理页面，让运营/开发在排查清楚问题后手动"重新投递"这条消息
 *
 * 本demo简化为只打印日志，实际项目里可以在这里接上你熟悉的"内部管理dashboard"，
 * 加一个失败消息列表页面。
 */
@Slf4j
@Component
public class DeadLetterConsumer {

    @KafkaListener(topics = MARKETING_EVENTS_DLT, groupId = "dlt-monitor-group")
    public void consumeDeadLetter(ConsumerRecord<String, MarketingEvent> record, Acknowledgment ack) {
        log.error("【死信队列监控】收到无法正常处理的消息！customerId(key)={}，消息内容={}，" +
                        "来自原始分区={}，原始offset={}，需要人工介入排查",
                record.key(), record.value(), record.partition(), record.offset());

        // 这里可以接：写入failed_messages表 / 发送告警通知 / 记录到监控系统
        // ...

        ack.acknowledge();
    }
}
