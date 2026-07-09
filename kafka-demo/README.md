# Spring Boot + Kafka 常用场景最小示例工程

跟你之前学的 redis-demo 是同一个思路：不追求"标准示例工程"的完整度，而是把生产环境
最常踩坑、最常用到的几个 Kafka 场景各写一个能跑通、能观察到效果的最小实现，方便你
拆解代码理解每一行在解决什么问题。

## 覆盖的场景

| 场景 | 对应代码 | 解决什么问题 |
|---|---|---|
| 顺序保证 | `MarketingEventProducer` 用 customerId 做 Key | 同一个客户的多条事件必须按顺序处理 |
| 手动ACK | `BasicEventConsumer` | 避免"处理到一半服务重启导致消息丢失" |
| 幂等消费 | `IdempotentEventConsumer` + Redis | 避免"消息重复投递导致业务被重复执行"（比如重复发权益） |
| 失败重试 + 死信队列 | `RetryDlqConsumer` + `DeadLetterConsumer` | 避免"一条坏消息卡住整个分区"或"失败消息悄悄丢失" |
| 批量消费 | `BatchLoanResultConsumer` | 高吞吐场景（比如批量放款结果）减少数据库交互次数 |

## 1. 准备环境

```bash
docker compose up -d
```

这会同时起一个单节点 Kafka（KRaft模式，不需要Zookeeper）和一个 Redis（幂等消费场景要用）。

## 2. 启动项目

```bash
mvn spring-boot:run
```

默认端口 `8081`（跟 redis-demo 的 8080 不冲突，两个项目可以同时跑）。

启动后观察日志，会看到几个消费者组各自订阅了 `marketing-events` 这个topic：
`basic-consumer-group`、`idempotent-consumer-group`、`retry-demo-group`、
`batch-consumer-group`，外加监听死信主题的 `dlt-monitor-group`。

**注意**：因为这5个消费者组都订阅了同一个topic，你调用 `/api/events/send` 发一条消息，
这几个消费者组的日志都会各自打印一遍（这是 Kafka "发布-订阅"模型的正常行为——不同
消费者组之间互不影响，各自独立消费全量消息）。测试某个场景时，建议主要看对应消费者
打的日志，忽略其他消费者组的日志。

## 3. 逐个场景验证

### 3.1 顺序保证

```bash
curl -X POST "http://localhost:8081/api/events/simulate-ordering?customerId=customer-9001"
```

连续发5条同一个客户的消息，去看 `BasicEventConsumer` 打的日志，重点看"分区="这个字段，
应该5条消息都是同一个分区号，并且 offset 是连续递增的、日志打印顺序和发送顺序一致。

### 3.2 手动ACK

```bash
curl -X POST "http://localhost:8081/api/events/send"
```

`BasicEventConsumer` 的日志里能看到"处理成功后手动提交offset"这行注释对应的逻辑。
可以试着在 `ack.acknowledge()` 那行之前故意加一行 `throw new RuntimeException()` 再重启
应用，会发现这条消息重启后又被重新消费了一次——这就是手动ACK "至少一次" 语义的直观体现。

### 3.3 幂等消费

```bash
curl -X POST "http://localhost:8081/api/events/send-duplicate?customerId=customer-2002"
```

去看 `IdempotentEventConsumer` 的日志，应该看到两行日志：
第一次"首次处理，执行真正的业务逻辑"，第二次"已处理过，跳过业务逻辑"。

### 3.4 失败重试 + 死信队列

```bash
curl -X POST "http://localhost:8081/api/events/send-poison?customerId=customer-6666"
```

去看 `RetryDlqConsumer` 的日志，应该看到"模拟处理失败"打印3次（1次首次尝试+2次重试，
每次间隔1秒），最后 `DeadLetterConsumer` 打印一条"收到无法正常处理的消息"的日志，
说明这条消息被自动转发到了死信主题，没有卡住后续消息的消费。

### 3.5 批量消费

```bash
curl -X POST "http://localhost:8081/api/events/send-batch?count=50"
```

去看 `BatchLoanResultConsumer` 的日志，应该看到"一次性拉取到 N 条消息"（N可能小于50，
取决于消息生产和消费的速度差，一次poll能拉到多少取决于当时topic里积压了多少），
而不是像其他消费者组那样打50条独立的单条日志。

## 4. 代码里需要重点看的几个设计点

| 文件 | 重点看什么 |
|---|---|
| `MarketingEventProducer.java` | 为什么用 customerId 做 Key 就能保证顺序（Kafka分区路由规则） |
| `KafkaErrorHandlingConfig.java` | `DefaultErrorHandler` + `FixedBackOff` + `DeadLetterPublishingRecoverer` 三者怎么组合实现"重试N次后转死信" |
| `IdempotentEventConsumer.java` | 为什么要"先处理业务，业务成功后才标记Redis+ack"，顺序反过来会有什么问题 |
| `BatchLoanResultConsumer.java` | 批量消费下单条消息异常要在方法内部自己捕获，不能让它冒泡到框架层，否则整批重复处理 |
| `application.yml` | `ack-mode: manual_immediate` 和 `enable-auto-commit: false` 为什么要配套一起改 |

## 5. 后续可以自己扩展的方向

- 把 `MarketingEventProducer.send()` 换成真实场景：营销活动状态变更后发消息，
  解耦"活动服务"和"短信通知服务"（正好可以接上你之前 redis-demo 里限流那部分的
  `SmsGatewayService`，变成"Kafka消费者收到事件 -> 调用限流保护的短信发送服务"）
- 批量消费者里接入真实的 MyBatis 批量insert，替换掉demo里的循环打日志
- 研究一下 Kafka 事务消息（`spring.kafka.producer.transaction-id-prefix`），
  用于"消费一条消息 + 处理业务 + 生产新消息"需要保证原子性的场景（比如消费订单消息，
  处理后必须同时发出一条"处理完成"的消息，两者要么都成功要么都失败）
