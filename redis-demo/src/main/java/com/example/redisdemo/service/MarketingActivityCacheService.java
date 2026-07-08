package com.example.redisdemo.service;

import com.example.redisdemo.entity.MarketingActivity;
import com.example.redisdemo.repository.MarketingActivityRepository;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * 缓存核心服务：标准的 Cache-Aside（旁路缓存）模式。
 *
 * 读流程：
 *   1. 先查缓存，命中直接返回
 *   2. 没命中，加锁（防击穿），再查一次缓存（双重检查，防止排队等锁的线程重复查库）
 *   3. 还是没有，查数据库
 *   4. 库里也没有 -> 缓存一个空值占位（防穿透），TTL 设置得比正常数据短
 *   5. 库里有 -> 正常写入缓存，TTL 加上随机偏移量（防雪崩）
 *
 * 对应解决的三个经典问题：
 *   - 缓存穿透：查询一个数据库里根本不存在的 key，每次都绕过缓存打到 DB。
 *     解法：空值也缓存（本例），或者用布隆过滤器提前拦截。
 *   - 缓存击穿：某个热点 key 突然过期，大量并发同时穿到 DB。
 *     解法：用分布式锁保证同一时刻只有一个线程去查 DB 并重建缓存，其它线程等待或读旧值。
 *   - 缓存雪崩：大量 key 在同一时间集中过期，瞬间压垮 DB。
 *     解法：TTL 加一个随机数，让过期时间分散开，不要让所有 key 同一秒过期。
 */
@Slf4j
@Service
public class MarketingActivityCacheService {

    private static final String CACHE_KEY_PREFIX = "marketing:activity:";
    private static final String LOCK_KEY_PREFIX = "lock:marketing:activity:";
    private static final String NULL_PLACEHOLDER = "__NULL__";

    private static final long BASE_TTL_SECONDS = 300;      // 基础缓存时间 5 分钟
    private static final long NULL_TTL_SECONDS = 60;       // 空值缓存时间短一些，避免真实数据插入后长期查不到
    private static final long RANDOM_TTL_BOUND_SECONDS = 60; // 雪崩防护：随机再加 0~60 秒

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private RedissonClient redissonClient;

    @Autowired
    private MarketingActivityRepository repository;

    public MarketingActivity getById(Long id) {
        String cacheKey = CACHE_KEY_PREFIX + id;

        // 1. 先查缓存
        Object cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            if (NULL_PLACEHOLDER.equals(cached)) {
                log.info("命中空值占位缓存，key={}，直接返回 null（防止穿透打到DB）", cacheKey);
                return null;
            }
            log.info("缓存命中，key={}", cacheKey);
            return (MarketingActivity) cached;
        }

        // 2. 没命中，加锁防击穿
        String lockKey = LOCK_KEY_PREFIX + id;
        RLock lock = redissonClient.getLock(lockKey);
        try {
            // 最多等 3 秒拿锁；锁本身用 watch dog 自动续期（leaseTime=-1）
            boolean acquired = lock.tryLock(3, -1, TimeUnit.SECONDS);
            if (!acquired) {
                // 拿不到锁说明有人正在重建缓存，简单起见这里直接查一次库兜底
                // 生产环境也可以选择短暂重试或返回"系统繁忙，请重试"
                log.warn("获取锁超时，直接降级查询数据库，key={}", lockKey);
                return repository.selectById(id);
            }

            // 3. 双重检查：等锁的这段时间，可能已经有其他线程重建好了缓存
            Object doubleCheck = redisTemplate.opsForValue().get(cacheKey);
            if (doubleCheck != null) {
                log.info("双重检查命中缓存（说明前一个线程已经重建完毕），key={}", cacheKey);
                return NULL_PLACEHOLDER.equals(doubleCheck) ? null : (MarketingActivity) doubleCheck;
            }

            // 4. 真正查数据库
            MarketingActivity activity = repository.selectById(id);

            if (activity == null) {
                // 防穿透：空值也缓存，但 TTL 短一点
                redisTemplate.opsForValue().set(cacheKey, NULL_PLACEHOLDER, NULL_TTL_SECONDS, TimeUnit.SECONDS);
                log.info("数据库也查不到，写入空值占位缓存，key={}", cacheKey);
                return null;
            }

            // 5. 防雪崩：TTL 加随机偏移
            long ttl = BASE_TTL_SECONDS + ThreadLocalRandom.current().nextLong(RANDOM_TTL_BOUND_SECONDS);
            redisTemplate.opsForValue().set(cacheKey, activity, ttl, TimeUnit.SECONDS);
            log.info("写入缓存，key={}，ttl={}秒", cacheKey, ttl);
            return activity;

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("获取锁被中断", e);
        } finally {
            // 只释放自己持有的锁，避免释放了别人的锁
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    /** 主动删除缓存，用于数据更新后的缓存失效（写操作时调用） */
    public void evict(Long id) {
        redisTemplate.delete(CACHE_KEY_PREFIX + id);
    }
}
