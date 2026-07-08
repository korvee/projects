package com.example.redisdemo.service;

import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * 分布式锁典型场景：批量营销任务防止被并发重复触发。
 *
 * 现实背景：零售贷款批量营销任务可能由定时任务触发，如果应用是多实例部署
 * （比如3台机器都跑着同一个 @Scheduled 定时任务），Java 自带的锁只能锁住
 * 单台机器内的线程，锁不住"3台机器同时抢到同一个任务"这件事，这时候就
 * 必须用分布式锁。
 *
 * 关键点：
 *   - tryLock(waitTime, leaseTime, unit)：leaseTime 传 -1 表示交给 Redisson
 *     的 watch dog 自动续期（默认每 10 秒续到 30 秒），业务逻辑执行多久都不怕锁提前过期，
 *     这是 Redisson 相比自己手写 setnx+expire 最大的优势之一。
 *   - 一定要在 finally 里判断 isHeldByCurrentThread() 再 unlock，
 *     否则如果锁已经因为异常等原因被别人持有，你会误解别人的锁。
 *   - RLock 默认是可重入的：同一个线程可以对同一把锁重复加锁而不会死锁自己。
 */
@Slf4j
@Service
public class BatchTaskLockService {

    private static final String LOCK_KEY_PREFIX = "lock:batch-task:";

    @Autowired
    private RedissonClient redissonClient;

    /**
     * @param taskId     任务标识，比如"2026-07-08-零售贷款批量营销"
     * @param workerName 当前执行者标识（模拟不同机器/线程），仅用于日志观察
     * @return 是否是本次调用真正执行了任务（true=抢到锁并执行，false=没抢到，任务已被别人在跑）
     */
    public boolean runBatchTaskIfNotRunning(String taskId, String workerName) {
        RLock lock = redissonClient.getLock(LOCK_KEY_PREFIX + taskId);
        boolean acquired = false;
        try {
            // 等待 0 秒：抢不到立刻返回，不排队。这符合"防止重复执行"的语义——
            // 别人在跑就直接放弃，而不是排队等着轮到自己再跑一遍。
            acquired = lock.tryLock(0, -1, TimeUnit.SECONDS);
            if (!acquired) {
                log.info("[{}] 未抢到锁，任务 [{}] 已在被其他实例执行，本次跳过", workerName, taskId);
                return false;
            }

            log.info("[{}] 抢到锁，开始执行任务 [{}]", workerName, taskId);
            // 模拟真实的批量处理耗时（比如批量给客户发送营销短信/计算名单）
            simulateHeavyWork();
            log.info("[{}] 任务 [{}] 执行完成", workerName, taskId);
            return true;

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        } finally {
            if (acquired && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    private void simulateHeavyWork() {
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
