package com.example.redisdemo.repository;

import com.example.redisdemo.entity.MarketingActivity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 模拟数据库。用一个内存 Map 代替真实的 MySQL/SQL Server 表，
 * 并且故意加了 200ms 延迟 + 查询计数器，方便你直观感受：
 *   有缓存 vs 没缓存，DB 被打的次数差多少。
 */
@Slf4j
@Repository
public class MarketingActivityRepository {

    // id=99 故意不存在，用来演示"查询空结果"（缓存穿透）的场景
    private final Map<Long, MarketingActivity> fakeDb = new ConcurrentHashMap<>(Map.of(
            1L, new MarketingActivity(1L, "私行高净值客户专属理财节", "PRIVATE_WEALTH", "费率9折"),
            2L, new MarketingActivity(2L, "零售信贷批量放款活动", "RETAIL_LOAN", "利率优惠0.5%")
    ));

    /** 统计真正打到"数据库"的次数，用来验证缓存是否生效 */
    private final AtomicInteger dbQueryCount = new AtomicInteger(0);

    public MarketingActivity selectById(Long id) {
        dbQueryCount.incrementAndGet();
        log.info(">>> 真实查询了数据库！当前累计查询数据库次数 = {}", dbQueryCount.get());
        try {
            // 模拟一次真实 SQL 查询的耗时
            Thread.sleep(200);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return fakeDb.get(id);
    }

    public int getDbQueryCount() {
        return dbQueryCount.get();
    }

    public void resetCounter() {
        dbQueryCount.set(0);
    }
}
