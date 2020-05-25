package com.liwei.demo;

import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.UUID;

@SpringBootTest
class DemoApplicationTests {
    @Autowired
    StringRedisTemplate stringRedisTemplate;

    @Autowired
    RedissonClient redissonClient;

    @Test
    public void testRedis() {
        //key:hello  value:world
        ValueOperations<String, String> ops = stringRedisTemplate.opsForValue();
        //存值
        ops.set("hello","world:"+ UUID.randomUUID().toString());
        System.out.println("存入的key为：hello");

        //取值
        String value = ops.get("hello");
        System.out.println("取出的值为："+value);

    }

    @Test
    public void testRedisson() {
        System.out.println(redissonClient);
    }

}
