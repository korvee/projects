package com.example.redisdemo.controller;

import com.example.redisdemo.entity.MarketingActivity;
import com.example.redisdemo.repository.MarketingActivityRepository;
import com.example.redisdemo.service.MarketingActivityCacheService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/activities")
public class MarketingActivityController {

    @Autowired
    private MarketingActivityCacheService cacheService;

    @Autowired
    private MarketingActivityRepository repository;

    /**
     * 查询营销活动。
     * 试试：
     *   GET /api/activities/1   -> 存在的数据，第二次调用会命中缓存，速度明显变快
     *   GET /api/activities/99  -> 不存在的数据，验证缓存穿透防护（多次调用DB查询次数不会增加）
     */
    @GetMapping("/{id}")
    public MarketingActivity getActivity(@PathVariable Long id) {
        return cacheService.getById(id);
    }

    @DeleteMapping("/{id}/cache")
    public String evictCache(@PathVariable Long id) {
        cacheService.evict(id);
        return "已清除 id=" + id + " 的缓存";
    }

    /** 查看数据库真实被查询了多少次，用来验证缓存有没有生效 */
    @GetMapping("/_debug/db-query-count")
    public String dbQueryCount() {
        return "数据库累计被查询次数 = " + repository.getDbQueryCount();
    }

    @PostMapping("/_debug/reset-counter")
    public String resetCounter() {
        repository.resetCounter();
        return "计数器已重置";
    }
}
