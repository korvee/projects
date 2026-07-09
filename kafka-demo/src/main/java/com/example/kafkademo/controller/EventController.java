package com.example.kafkademo.controller;

import com.example.kafkademo.entity.MarketingEvent;
import com.example.kafkademo.producer.MarketingEventProducer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.UUID;

@RestController
@RequestMapping("/api/events")
public class EventController {

    @Autowired
    private MarketingEventProducer producer;

    /** 发送一条普通事件，观察 basic-consumer-group / idempotent-consumer-group / retry-demo-group 的日志都会各自消费到 */
    @PostMapping("/send")
    public String send(@RequestParam(defaultValue = "customer-1001") String customerId,
                        @RequestParam(defaultValue = "ACTIVITY_JOINED") String eventType) {
        MarketingEvent event = new MarketingEvent(
                UUID.randomUUID().toString(), customerId, "activity-2026-summer",
                eventType, "客户参与了活动", LocalDateTime.now());
        producer.send(event);
        return "已发送事件，eventId=" + event.getEventId();
    }

    /**
     * 顺序性演示：连续发5条同一个customerId的消息，观察 BasicEventConsumer 的日志，
     * 应该能看到这5条消息全部落在同一个分区，且按发送顺序被消费。
     */
    @PostMapping("/simulate-ordering")
    public String simulateOrdering(@RequestParam(defaultValue = "customer-9001") String customerId) {
        for (int i = 1; i <= 5; i++) {
            MarketingEvent event = new MarketingEvent(
                    UUID.randomUUID().toString(), customerId, "activity-2026-summer",
                    "STEP_" + i, "第" + i + "步操作", LocalDateTime.now());
            producer.send(event);
        }
        return "已连续发送5条customerId=" + customerId + "的事件，去看BasicEventConsumer的日志，" +
                "确认它们落在同一个分区并按顺序被消费";
    }

    /**
     * 重试+死信队列演示：发一条必然处理失败的消息。
     * 观察 RetryDlqConsumer 的日志：会看到重试2次（间隔1秒），最终失败后，
     * DeadLetterConsumer 会打印一条"收到无法正常处理的消息"的日志。
     */
    @PostMapping("/send-poison")
    public String sendPoison(@RequestParam(defaultValue = "customer-6666") String customerId) {
        MarketingEvent event = new MarketingEvent(
                UUID.randomUUID().toString(), customerId, "activity-2026-summer",
                "POISON_TEST", "这条消息会触发处理失败", LocalDateTime.now());
        producer.send(event);
        return "已发送毒消息，eventId=" + event.getEventId() + "，观察日志里的重试和死信队列流转过程";
    }

    /**
     * 幂等消费演示：故意发送两条 eventId 完全相同的消息（模拟网络重试导致的重复投递）。
     * 观察 IdempotentEventConsumer 的日志：第一条会真正处理，第二条会被识别为
     * "已处理过"直接跳过。
     */
    @PostMapping("/send-duplicate")
    public String sendDuplicate(@RequestParam(defaultValue = "customer-2002") String customerId) {
        String fixedEventId = UUID.randomUUID().toString();
        MarketingEvent event = new MarketingEvent(
                fixedEventId, customerId, "activity-2026-summer",
                "ACTIVITY_JOINED", "模拟重复投递的消息", LocalDateTime.now());

        producer.send(event);
        producer.send(event); // 用完全相同的eventId再发一次，模拟重复投递

        return "已用相同eventId=" + fixedEventId + "连续发送2次，观察IdempotentEventConsumer的日志";
    }

    /**
     * 批量消费演示：一次性发送大量消息，模拟"批量放款结果"场景。
     * 观察 BatchLoanResultConsumer 的日志，会看到消息被打包成一批一起处理，
     * 而不是像其他几个消费者组那样一条一条打日志。
     */
    @PostMapping("/send-batch")
    public String sendBatch(@RequestParam(defaultValue = "50") int count) {
        for (int i = 1; i <= count; i++) {
            MarketingEvent event = new MarketingEvent(
                    UUID.randomUUID().toString(), "customer-" + (7000 + i), "loan-batch-2026-07",
                    "LOAN_BATCH_RESULT", "第" + i + "笔放款结果", LocalDateTime.now());
            producer.send(event);
        }
        return "已发送" + count + "条批量放款结果事件，观察BatchLoanResultConsumer日志里的批量处理效果";
    }
}
