# Redis 缓存 + 分布式锁 最小示例工程

这是一个可以直接跑起来的 Spring Boot 3 + Redis 最小示例，包含两个场景，对应你实际工作中最常用到的：

1. **缓存场景**（`MarketingActivityCacheService`）：Cache-Aside 模式，处理缓存穿透 / 击穿 / 雪崩
2. **分布式锁场景**（`BatchTaskLockService`）：用 Redisson 防止批量任务被多实例并发重复执行

## 1. 准备环境

需要本地跑一个 Redis（没有的话用 Docker 最快）：

```bash
docker run -d --name redis-demo -p 6379:6379 redis:7
```

## 2. 启动项目

```bash
mvn spring-boot:run
```

默认端口 `8080`。

## 3. 测试缓存场景

**正常查询 + 观察缓存命中：**

```bash
# 重置计数器
curl -X POST http://localhost:8080/api/activities/_debug/reset-counter

# 第一次查询 id=1，会真正查一次"数据库"（日志里能看到 200ms 左右延迟）
curl http://localhost:8080/api/activities/1

# 第二次查询同一个 id，直接命中缓存，几乎瞬间返回
curl http://localhost:8080/api/activities/1

# 查看数据库累计被查询次数：应该还是 1，说明第二次走的是缓存
curl http://localhost:8080/api/activities/_debug/db-query-count
```

**验证防穿透（查一个数据库里根本不存在的 id）：**

```bash
curl -X POST http://localhost:8080/api/activities/_debug/reset-counter

curl http://localhost:8080/api/activities/99   # 数据库里没有id=99
curl http://localhost:8080/api/activities/99
curl http://localhost:8080/api/activities/99

# 数据库查询次数应该只有 1（第一次没查到，写入空值占位缓存，
# 后续请求都被空值缓存挡住了，不会一直打DB）
curl http://localhost:8080/api/activities/_debug/db-query-count
```

**验证防击穿（可以用 `ab` 或 `wrk` 并发压测同一个刚过期的 key，观察 DB 查询次数不会随并发数暴涨）：**

```bash
# 先清掉id=1的缓存，制造"缓存刚好过期"的场景
curl -X DELETE http://localhost:8080/api/activities/1/cache
curl -X POST http://localhost:8080/api/activities/_debug/reset-counter

# 并发 20 个请求同时打过来（需要安装 apache bench: apt install apache2-utils）
ab -n 20 -c 20 http://localhost:8080/api/activities/1

# 理想情况下数据库只被真正查询了 1 次，其余19个请求都在等锁或读到了重建后的缓存
curl http://localhost:8080/api/activities/_debug/db-query-count
```

## 4. 测试分布式锁场景

```bash
curl http://localhost:8080/api/batch-task/simulate-concurrent?taskId=retail-loan-batch
```

这个接口内部用 3 个线程模拟"3台服务器同时触发同一个批量任务"。返回结果里应该只有一个
`实际执行=true`，其余都是 `实际执行=false`，说明分布式锁生效了，避免了同一批客户被
重复触发营销任务（比如重复发短信、重复计算佣金）。

如果你想直观感受"没有锁会怎样"，可以把 `BatchTaskLockService` 里加锁/解锁的代码临时注释掉，
再跑一次这个接口，会看到 3 个都变成 `true`。

## 5. 代码里需要重点看的几个设计点

| 文件 | 重点看什么 |
|---|---|
| `RedisConfig.java` | 为什么不用默认的 JDK 序列化，换成 Jackson 之后 redis-cli 里看到的数据是可读的 |
| `RedissonConfig.java` | 分布式锁客户端和缓存用的 RedisTemplate 是两条独立连接 |
| `MarketingActivityCacheService.java` | 双重检查锁（Double-Check）、空值占位防穿透、随机TTL防雪崩 三个技巧怎么组合在一起用 |
| `BatchTaskLockService.java` | `tryLock(waitTime, leaseTime, unit)` 中 `leaseTime=-1` 触发 watch dog 自动续期；`finally` 里为什么要判断 `isHeldByCurrentThread()` 再解锁 |

## 6. 后续可以自己扩展的方向

- 把 `MarketingActivityRepository` 换成真实的 JPA/MyBatis 查询你自己的表
- 给 `evict()` 接一个 MQ 监听，实现"数据库更新 -> 发消息 -> 消费者清缓存"的最终一致性方案
- 把 `BatchTaskLockService` 接到 `@Scheduled` 定时任务上，模拟真实的多实例部署防重复调度场景
