package com.liwei.demo.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @ClassName RedissonConfig
 * @Description: TODO
 * @Author LiWei
 * @Date 2020/5/25
 **/
@Configuration
public class RedissonConfig {
    /*
     * @MethodName: redissonClient
     * @Description: redisson的使用都是通过redissonClient进行的
     * @Param: []
     * @Return: org.redisson.api.RedissonClient
     * @Author: LiWei
     * @Date: 2020/5/25
    **/
    @Bean(destroyMethod = "shutdown")
    public RedissonClient redissonClient(){
        Config config = new Config();
        config.useSingleServer().setAddress("redis://127.0.0.1:6379");
        RedissonClient redissonClient = Redisson.create(config);
        return redissonClient;
    }
}
