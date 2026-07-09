package com.example.kafkademo.producer;

import com.example.kafkademo.entity.MarketingEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import static com.example.kafkademo.config.KafkaTopicConfig.MARKETING_EVENTS_TOPIC;

/**
 * 营销事件生产者。
 *
 * 关键点：send() 的第二个参数是 Kafka 消息的 Key，这里固定用 customerId。
 * Kafka 保证"相同 Key 的消息一定进同一个分区"，而同一个分区内的消息是严格
 * 按发送顺序存储和消费的。所以只要给同一个客户的所有事件都用他的 customerId
 * 做 Key，就能保证这个客户的事件被顺序处理（比如"先加入活动"一定在
 * "活动完成"之前被消费到），不会因为分布在不同分区被并发乱序处理。
 *
 * 反过来说：如果 Key 传 null 或者每次随机，Kafka 会用轮询等方式把消息打散到
 * 各个分区，吞吐量更高，但完全不保证任何顺序——这是纯粹的"用顺序换吞吐"的权衡，
 * 要根据业务是否关心顺序来选择。
 */
@Slf4j
@Component
public class MarketingEventProducer {

    @Autowired
    private KafkaTemplate<Object, Object> kafkaTemplate;

    public void send(MarketingEvent event) {
        kafkaTemplate.send(MARKETING_EVENTS_TOPIC, event.getCustomerId(), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("消息发送失败，eventId={}", event.getEventId(), ex);
                    } else {
                        log.info("消息发送成功，eventId={}，分区={}，offset={}",
                                event.getEventId(),
                                result.getRecordMetadata().partition(),
                                result.getRecordMetadata().offset());
                    }
                });
    }
}
