package com.hmdp.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author asleer
 * @description Redisson配置类，使用redisson可以解决setnx实现分布式锁存在的四个问题：
 *              不可重入、不可重试、超时释放、主从一致性的问题
 * @date 2022/11/5 10:52
 */

@Configuration
public class RedissonConfig {

    @Bean
    public RedissonClient redissonClient(){
        // 配置
        Config config = new Config();
        config.useSingleServer()
                .setAddress("redis://192.168.248.130:6379")
                .setPassword("123321");
        // 创建RedissonClient对象
        return Redisson.create(config);
    }
}
