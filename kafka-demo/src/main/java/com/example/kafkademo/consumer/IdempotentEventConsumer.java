package com.example.kafkademo.consumer;

import com.example.kafkademo.entity.MarketingEvent;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

import static com.example.kafkademo.config.KafkaTopicConfig.MARKETING_EVENTS_TOPIC;

/**
 * 幂等消费者：解决"同一条消息被消费两次"的问题。
 *
 * 为什么会重复消费？Kafka 的设计是"至少一次投递"（at-least-once），不是"恰好一次"。
 * 常见触发场景：消费者处理完业务逻辑，正准备调用 ack.acknowledge() 提交offset时，
 * 服务突然重启/网络抖动——offset没提交成功，Kafka认为这条消息还没被消费，下次
 * 会重新投递。但业务逻辑其实已经执行过一次了，如果不做幂等处理，就会出现
 * "客户被重复发放一次营销权益/重复扣款"这类事故。
 *
 * 解法：复用你在 redis-demo 里学的 SADD 去重思路——用消息的业务唯一键（eventId，
 * 不是Kafka的offset）在Redis里做"检查并标记"，标记失败（说明处理过）就直接跳过
 * 业务逻辑，只ack不处理，这样重复投递的消息不会被重复执行。
 *
 * 注意实现上一个容易踩的坑：一定要"先处理业务，业务成功后才ack"，而不是"先标记
 * Redis，再处理业务"——如果标记了Redis但业务处理失败，这条消息就永远不会被
 * 重新处理了（因为Redis里已经标记成"处理过"）。正确顺序是：业务处理成功之后，
 * 再标记Redis + ack，两者应尽量保证一致（本demo为了简化没有引入分布式事务，
 * 生产环境如果要求绝对严格，可以把"标记Redis"和"业务落库"放进同一个数据库事务，
 * 通过数据库的唯一约束做最终兜底）。
 */
@Slf4j
@Component
public class IdempotentEventConsumer {

    private static final String PROCESSED_KEY_PREFIX = "kafka:processed:event:";
    private static final long DEDUP_TTL_HOURS = 24;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @KafkaListener(topics = MARKETING_EVENTS_TOPIC, groupId = "idempotent-consumer-group")
    public void consume(ConsumerRecord<String, MarketingEvent> record, Acknowledgment ack) {
        MarketingEvent event = record.value();
        String dedupKey = PROCESSED_KEY_PREFIX + event.getEventId();

        // SETNX 语义：key不存在才设置成功，返回true；已存在返回false。这一步是原子的。
        Boolean firstTime = redisTemplate.opsForValue()
                .setIfAbsent(dedupKey, "1", DEDUP_TTL_HOURS, TimeUnit.HOURS);

        if (Boolean.FALSE.equals(firstTime)) {
            log.info("【幂等消费者】eventId={} 已处理过，跳过业务逻辑，直接ack", event.getEventId());
            ack.acknowledge();
            return;
        }

        log.info("【幂等消费者】eventId={} 首次处理，执行真正的业务逻辑", event.getEventId());
        // 真实业务处理逻辑写在这里
        // ...

        ack.acknowledge();
    }
}
