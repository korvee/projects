package com.example.kafkademo.consumer;

import com.example.kafkademo.entity.MarketingEvent;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import static com.example.kafkademo.config.KafkaTopicConfig.MARKETING_EVENTS_TOPIC;

/**
 * 最基础的消费者：演示手动ACK，以及消息的分区/顺序信息。
 *
 * 手动ACK的意义：如果用自动提交（enable-auto-commit=true），Kafka 客户端会按固定
 * 时间间隔自动提交offset，这意味着"消息已经被拉取到本地"和"消息已经被成功处理"
 * 是两件不保证同步的事——如果处理到一半服务重启，这条消息就永久丢失了（offset已经
 * 提交过去了，Kafka认为你处理完了）。手动ACK改成"业务代码明确处理成功后，才调用
 * acknowledge()"，这样即使服务重启，没ack的消息下次还会被重新投递，保证"至少消费一次"。
 */
@Slf4j
@Component
public class BasicEventConsumer {

    @KafkaListener(topics = MARKETING_EVENTS_TOPIC, groupId = "basic-consumer-group")
    public void consume(ConsumerRecord<String, MarketingEvent> record, Acknowledgment ack) {
        MarketingEvent event = record.value();
        log.info("【基础消费者】收到消息：分区={}，offset={}，key(customerId)={}，事件={}",
                record.partition(), record.offset(), record.key(), event);

        // 真实业务处理逻辑写在这里，比如更新活动参与状态、记录日志等
        // ...

        // 处理成功后手动提交offset，Kafka才会认为这条消息被这个消费者组"消费完成"
        ack.acknowledge();
    }
}
