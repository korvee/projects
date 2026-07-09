package com.example.kafkademo.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * 主题（Topic）定义。
 *
 * 用 Spring Kafka 提供的 TopicBuilder 声明式创建 Topic，应用启动时会自动
 * 检查这些 Topic 是否存在，不存在就自动创建（依赖 Kafka broker 允许自动建topic
 * 或者这里显式创建，生产环境更推荐显式创建并规划好分区数，而不是依赖自动建topic）。
 *
 * 分区数（partitions）怎么定：分区数决定了这个topic最多能被多少个消费者实例
 * 并行消费（同一个消费者组内，一个分区只能被一个消费者实例消费）。这里设置3个
 * 分区，意味着你最多可以部署3个消费者实例并行处理，同时又保证了"同一个客户的
 * 消息因为用customerId做key，会稳定进同一个分区"，从而保证顺序。
 */
@Configuration
public class KafkaTopicConfig {

    public static final String MARKETING_EVENTS_TOPIC = "marketing-events";
    public static final String MARKETING_EVENTS_DLT = "marketing-events.DLT";

    @Bean
    public NewTopic marketingEventsTopic() {
        return TopicBuilder.name(MARKETING_EVENTS_TOPIC)
                .partitions(3)
                .replicas(1)
                .build();
    }

    /**
     * 死信主题（Dead Letter Topic）：消息重试多次仍然失败后，会被自动转发到这里，
     * 而不是无限重试卡住整个分区，或者悄无声息地丢弃。运维/开发可以单独监控
     * 这个topic，人工介入处理失败的消息。
     */
    @Bean
    public NewTopic marketingEventsDlt() {
        return TopicBuilder.name(MARKETING_EVENTS_DLT)
                .partitions(1)
                .replicas(1)
                .build();
    }
}
