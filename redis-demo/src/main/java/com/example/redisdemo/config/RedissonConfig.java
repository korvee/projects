package com.example.redisdemo.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Redisson 客户端配置。
 *
 * 这里显式创建 RedissonClient，而不是完全依赖 starter 的自动配置，
 * 是为了让你能清楚看到"分布式锁工具"和"缓存用的 RedisTemplate"
 * 其实是两条独立的连接/客户端，二者互不干扰。
 *
 * 生产环境如果是哨兵/集群模式，把 useSingleServer 换成
 * useSentinelServers() / useClusterServers() 即可，锁的使用代码不用改。
 */
@Configuration
public class RedissonConfig {

    @Value("${redisson.address:redis://127.0.0.1:6379}")
    private String address;

    @Value("${redisson.password:}")
    private String password;

    @Bean
    public RedissonClient redissonClient() {
        Config config = new Config();
        var serverConfig = config.useSingleServer()
                .setAddress(address)
                .setConnectionMinimumIdleSize(2)
                .setConnectionPoolSize(10);
        if (password != null && !password.isBlank()) {
            serverConfig.setPassword(password);
        }
        return Redisson.create(config);
    }
}
