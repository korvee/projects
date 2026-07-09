package com.example.kafkademo.consumer;

import com.example.kafkademo.entity.MarketingEvent;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.util.List;

import static com.example.kafkademo.config.KafkaTopicConfig.MARKETING_EVENTS_TOPIC;

/**
 * 批量消费者：模拟"零售贷款批量放款结果异步入库"这类高吞吐场景。
 *
 * 逐条消费 vs 批量消费的差别：如果一次批量任务产生几万条"放款结果"消息，
 * 逐条消费意味着每条消息都要单独执行一次"处理业务 + 写数据库"，几万次数据库
 * 单条insert，光是网络往返开销就很可观。批量消费把一次 poll 拉取到的所有消息
 * （由 max.poll.records 控制，默认500条）打包成 List 一次性交给业务代码，
 * 可以用 MyBatis 的批量insert（一条SQL插入多行）大幅减少数据库交互次数，
 * 这是高吞吐场景下典型的"用延迟换吞吐"的优化思路——牺牲一点点单条消息的实时性
 * （要攒够一批或等到超时才处理），换取整体处理速度的数量级提升。
 *
 * 注意：批量消费下，一批消息里只要有一条抛异常，整批默认都会被判定失败重新投递
 * （除非你自己在批量处理逻辑里做了容错，跳过单条坏数据）。所以批量消费的业务代码
 * 通常需要自己在方法内部做好单条异常的捕获，避免一条脏数据拖累整批重复处理。
 *
 * 方法签名说明：批量消费模式下，第一个参数必须是 List<ConsumerRecord<K, V>>，
 * 而不是 List<V>。因为 Spring Kafka 的批量监听器需要 ConsumerRecord 的元数据
 * （分区、offset等）来正确解析参数。Acknowledgment 作为第二个参数。
 */
@Slf4j
@Component
public class BatchLoanResultConsumer {

    @KafkaListener(
            topics = MARKETING_EVENTS_TOPIC,
            groupId = "batch-consumer-group",
            containerFactory = "batchListenerContainerFactory"
    )
    public void consumeBatch(List<ConsumerRecord<String, MarketingEvent>> records, Acknowledgment ack) {
        log.info("【批量消费者】一次性拉取到 {} 条消息，开始批量处理", records.size());

        int successCount = 0;
        for (ConsumerRecord<String, MarketingEvent> record : records) {
            MarketingEvent event = record.value();
            try {
                // 真实场景这里应该是攒够一批之后，拼一条批量 INSERT ... VALUES (...),(...),(...)
                // 的SQL一次性落库，而不是像这里为了演示简化成循环处理
                log.debug("处理消息 eventId={}，分区={}，offset={}",
                        event.getEventId(), record.partition(), record.offset());
                successCount++;
            } catch (Exception e) {
                // 批量场景下，单条数据有问题不应该影响整批，捕获后记录下来单独处理即可
                log.error("批量处理中单条消息失败，eventId={}", event.getEventId(), e);
            }
        }

        log.info("【批量消费者】本批次处理完成，成功 {}/{} 条", successCount, records.size());
        ack.acknowledge();
    }
}
