package com.example.kafkademo.consumer;

import com.example.kafkademo.entity.MarketingEvent;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import static com.example.kafkademo.config.KafkaTopicConfig.MARKETING_EVENTS_TOPIC;

/**
 * 演示"失败重试 + 死信队列"的消费者。
 *
 * 用法上和 BasicEventConsumer 唯一的区别是指定了
 * containerFactory = "retryableListenerContainerFactory"，这个工厂配置了
 * DefaultErrorHandler（见 KafkaErrorHandlingConfig），业务代码本身完全不用写
 * 任何 try-catch 重试逻辑，直接把异常抛出去就行，框架会自动处理：
 *   抛异常 -> 等1秒 -> 重试 -> 还失败 -> 再等1秒 -> 再重试 -> 还失败
 *   -> 自动转发到死信主题，继续消费下一条消息（不会卡住整个分区）
 *
 * eventType="POISON_TEST" 是本demo故意制造的"必然失败"消息，用来触发这个流程，
 * 模拟真实场景里"消息内容有脏数据导致业务代码必然抛异常"的情况。
 */
@Slf4j
@Component
public class RetryDlqConsumer {

    @KafkaListener(
            topics = MARKETING_EVENTS_TOPIC,
            groupId = "retry-demo-group",
            containerFactory = "retryableListenerContainerFactory"
    )
    public void consume(ConsumerRecord<String, MarketingEvent> record, Acknowledgment ack) {
        MarketingEvent event = record.value();
        log.info("【重试演示消费者】收到消息，eventId={}，eventType={}", event.getEventId(), event.getEventType());

        if ("POISON_TEST".equals(event.getEventType())) {
            // 故意抛异常，模拟业务处理必然失败的场景（比如数据格式错误、下游服务持续不可用）
            log.warn("【重试演示消费者】eventId={} 模拟处理失败，即将触发重试机制", event.getEventId());
            throw new RuntimeException("模拟业务处理异常：eventId=" + event.getEventId());
        }

        log.info("【重试演示消费者】eventId={} 处理成功", event.getEventId());
        ack.acknowledge();
    }
}
