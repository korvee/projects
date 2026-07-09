package com.example.kafkademo.config;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

/**
 * 失败重试 + 死信队列（DLQ）配置。这是生产环境里最重要的可靠性保证之一——
 * 没有这套机制的话，消费者处理消息抛异常时，默认行为要么是消息丢失（自动ack场景），
 * 要么是同一条坏消息反复阻塞整个分区（不停重试同一条消息导致后面的消息也处理不了）。
 *
 * 这里的策略：
 *   1. 消息处理失败，先用 FixedBackOff 做几次本地重试（间隔1秒，最多重试2次，共尝试3次）
 *   2. 如果重试3次还是失败，判定为"这条消息处理不了"，通过 DeadLetterPublishingRecoverer
 *      自动把这条消息转发到死信主题 marketing-events.DLT，然后跳过它继续消费下一条，
 *      不会卡住整个分区
 *   3. 死信主题里的消息由专门的监控消费者处理（见 DeadLetterConsumer），
 *      可以记录到数据库、报警通知人工介入，而不是悄无声息地丢失
 */
@Slf4j
@Configuration
public class KafkaErrorHandlingConfig {

    @Autowired
    private ConsumerFactory<Object, Object> consumerFactory;

    @Autowired
    private KafkaTemplate<Object, Object> kafkaTemplate;

    /**
     * 专门给需要"重试+死信"能力的 @KafkaListener 使用，通过
     * @KafkaListener(containerFactory = "retryableListenerContainerFactory") 指定。
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<Object, Object> retryableListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<Object, Object> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);

        // 消息重试失败后，转发到 "原topic名.DLT" 这个死信主题
        // （DeadLetterPublishingRecoverer 默认命名规则就是 topic + ".DLT"，跟我们上面定义的一致）
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(kafkaTemplate);

        // FixedBackOff(间隔毫秒数, 最大重试次数)：间隔1秒，重试2次（加上第一次共尝试3次）
        DefaultErrorHandler errorHandler = new DefaultErrorHandler(recoverer, new FixedBackOff(1000L, 2L));

        // 也可以针对特定异常类型设置"不重试，直接进死信"，比如反序列化失败这种重试也没用的错误：
        // errorHandler.addNotRetryableExceptions(IllegalArgumentException.class);

        factory.setCommonErrorHandler(errorHandler);
        return factory;
    }

    /**
     * 批量消费场景使用的容器工厂，见 BatchLoanResultConsumer。
     * 通过 setBatchListener(true) 开启后，@KafkaListener 方法的入参从单条消息
     * 变成 List<消息>，一次 poll 拉取到的所有消息会打包一起交给业务代码处理。
     *
     * 注意：批量消费模式下，Acknowledgment 参数必须通过 setBatchListener 配合
     * 手动 ACK 模式使用。这里显式设置 ackMode 为 MANUAL_IMMEDIATE，确保
     * Acknowledgment 参数能被正确注入到方法签名中。
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<Object, Object> batchListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<Object, Object> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.setBatchListener(true);
        // 批量消费模式下必须显式设置 ackMode，否则 Acknowledgment 参数无法被正确注入
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
        return factory;
    }
}
