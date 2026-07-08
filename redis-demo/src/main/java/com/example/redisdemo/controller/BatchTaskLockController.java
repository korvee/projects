package com.example.redisdemo.controller;

import com.example.redisdemo.service.BatchTaskLockService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@RestController
@RequestMapping("/api/batch-task")
public class BatchTaskLockController {

    @Autowired
    private BatchTaskLockService lockService;

    /**
     * 模拟"3台机器同时触发同一个批量任务"的并发场景。
     * 试试：GET /api/batch-task/simulate-concurrent?taskId=retail-loan-batch
     *
     * 预期结果：simulatedInstances 里只有 1 个 true，其余全是 false ——
     * 说明分布式锁生效了，同一个任务不会被并发重复执行。
     * 如果把 BatchTaskLockService 里的锁去掉再跑一次，你会看到全部变成 true。
     */
    @GetMapping("/simulate-concurrent")
    public String simulateConcurrent(@RequestParam(defaultValue = "demo-task") String taskId) throws InterruptedException {
        int instanceCount = 3;
        ExecutorService executor = Executors.newFixedThreadPool(instanceCount);
        CountDownLatch latch = new CountDownLatch(instanceCount);
        List<String> results = new CopyOnWriteArrayList<>();

        for (int i = 1; i <= instanceCount; i++) {
            String workerName = "实例-" + i;
            executor.submit(() -> {
                try {
                    boolean executed = lockService.runBatchTaskIfNotRunning(taskId, workerName);
                    results.add(workerName + " -> 实际执行=" + executed);
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();
        return "任务[" + taskId + "]并发触发结果（应该只有一个执行成功）：\n" + String.join("\n", results);
    }
}
