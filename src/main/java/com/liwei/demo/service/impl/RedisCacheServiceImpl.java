package com.liwei.demo.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.TypeReference;
import com.liwei.demo.entity.Student;
import com.liwei.demo.service.RedisCacheService;
import jdk.nashorn.internal.runtime.JSONFunctions;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * @ClassName RedisCacheServiceImpl
 * @Description: TODO
 * @Author LiWei
 * @Date 2020/5/25
 **/
@Service
public class RedisCacheServiceImpl implements RedisCacheService {
    //本地缓存
    private Map<String, Object> cache = new HashMap<>();
    //redis缓存
    @Autowired
    StringRedisTemplate redisTemplate;

    @Autowired
    RedissonClient redissonClient;

    @Override
    public List<Student> getStudentList() {
        //方式一：本地缓存
        //List<Student> cache = this.getStudentLocalCache();

        //方式二：redis缓存形式
        //List<Student> cache = this.getStudentRedisCache();

        //方式三：单机redis缓存形式的优化，解决高并发下的缓存穿透，雪崩，击穿问题
        //List<Student> cache = this.getRedisCacheOptimize();

        //方式四：分布式redis缓存形式的优化，解决高并发下的缓存穿透，雪崩，击穿问题
        //List<Student> cache = this.getRedisCacheOptimizeWithLock();

        //方式五：redisson实现分布式锁功能
        List<Student> cache = this.getCacheWithRedissonLock();


        return cache;
    }

    /*
     * @MethodName: getCacheWithRedissonLock
     * @Description: 此方式解决了不用设置锁的自动续期功能，不会引发死锁，即使redis宕机也会自动释放锁
     * 自动续期由内置的看门狗来不停的监控，根据业务代码的时长不停的自动续期
     * @Param: []
     * @Return: java.util.List<com.liwei.demo.entity.Student>
     * @Author: LiWei
     * @Date: 2020/5/25
    **/
    private List<Student> getCacheWithRedissonLock() {
        RLock redissonLock = redissonClient.getLock("redissonLock");
        redissonLock.lock();
        try {
            List<Student> studentRedisCache = getStudentRedisCache();
            return studentRedisCache;
        }finally {
            redissonLock.unlock();
        }
    }

    /*
     * @MethodName: getRedisCacheOptimizeWithLock
     * @Description: redis实现分布式锁的功能，加锁解锁都是原子性操作，原子操作通过lua脚本来实现
     * @Param: []
     * @Return: java.util.List<com.liwei.demo.entity.Student>
     * @Author: LiWei
     * @Date: 2020/5/25
     **/
    private List<Student> getRedisCacheOptimizeWithLock() {
        //抢锁占坑的过程，true代表抢到锁了，false代表没有抢到
        //这里一定要设置过期时间，防止redis锁不能释放导致死锁
        //并且还应该是同一个用户的锁才删除锁，否则可能因为业务代码时间超长导致锁时间到期了，其他线程加了锁，这是需判断是不是自己加的的锁，不是就不删除
        //加锁原子性
        String token = UUID.randomUUID().toString();
        Boolean ifAbsent = redisTemplate.opsForValue().setIfAbsent("lock", "token", 300, TimeUnit.SECONDS);
        if (ifAbsent) {
            List<Student> studentRedisCache;
            try {
                //此处如果业务代码时间执行过长的话可以选择把锁的过期时间设置长一点即可
                studentRedisCache = getStudentRedisCache();
            } finally {
                String script = "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end";
                Long lock = redisTemplate.execute(new DefaultRedisScript<Long>(script, Long.class), Arrays.asList("lock"), token);
            }
            //此时需要使用lua脚本进行比较。防止在删除的过程中锁时间到期，就会删了别的用户加的锁
            //解锁需要原子性，此处不满足，还是不合适
            /*String lockValue = redisTemplate.opsForValue().get("lock");
            if (token.equals(lockValue)){
                redisTemplate.delete("lock");
            }*/
            return studentRedisCache;
        } else {
            try {
                Thread.sleep(300);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            //进行自旋操作，直到抢到锁为止
            return getRedisCacheOptimizeWithLock();
        }
    }

    /*
     * @MethodName: getRedisCacheOptimize
     * @Description: 在单机情况下是没有问题的，但是分布式条件下每个主机都会进行数据库查询操作
     * @Param: []
     * @Return: java.util.List<com.liwei.demo.entity.Student>
     * @Author: LiWei
     * @Date: 2020/5/25
     **/
    private List<Student> getRedisCacheOptimize() {
        /*1.空结果缓存---解决缓存穿透的问题
         * 2.合理设置key过期时间，避免key大面积失效，例如引入随机值---避免出现缓存雪崩
         * 3.对于热点数据加同步锁---解决缓存击穿
         * */
        String rdisValue = redisTemplate.opsForValue().get("all-student-redis");
        if (StringUtils.isEmpty(rdisValue)) {
            //1.缓存为空，则应该进入数据库查询，查询出来以后需要存入缓存中的操作放到锁操作里面，确保锁的时序问题
            List<Student> dbList = this.getLocalDataCacheOptimizeFromDB();
            return dbList;
        }

        //返回结果需要由json字符串转换为指定对象
        List<Student> result = JSON.parseObject(rdisValue, new TypeReference<List<Student>>() {
        });
        return result;
    }

    /*
     * @MethodName: getStudentRedisCache
     * @Description: 2.0以后的版本redis使用的是lettuce，底层使用的是netty，吞吐量非常高，但是在高并发的情况下会出现堆外内存溢出
     * OutOfDirectMemoryError,解决方案如下：
     * 1.升级lettuce，改源码，修复bug；2.使用低版本的jedis
     * @Param: []
     * @Return: java.util.List<com.liwei.demo.entity.Student>
     * @Author: LiWei
     * @Date: 2020/5/25
     **/
    private List<Student> getStudentRedisCache() {
        //加入缓存功能,value值使用json字符串,json字符串可以跨平台，非常方便
        String rdisValue = redisTemplate.opsForValue().get("all-student-redis");
        if (StringUtils.isEmpty(rdisValue)) {
            //1.缓存为空，则应该进入数据库查询
            List<Student> dbList = this.getLocalDataCacheFromDB();
            //2.查询出来以后需要存入缓存中
            String jsonData = JSON.toJSONString(dbList);
            redisTemplate.opsForValue().set("all-student-redis", jsonData);
            return dbList;
        }

        //返回结果需要由json字符串转换为指定对象
        List<Student> result = JSON.parseObject(rdisValue, new TypeReference<List<Student>>() {
        });
        return result;
    }

    /*
     * @MethodName: getStudentLocalCache
     * @Description: 本地缓存的形式
     * @Param: []
     * @Return: java.util.List<com.liwei.demo.entity.Student>
     * @Author: LiWei
     * @Date: 2020/5/25
     * 存在的问题，单机是没有问题的，分布式的情况下，同一个服务部署在多台机器上存在问题，缓存效果会出问题
     **/
    private List<Student> getStudentLocalCache() {
        List<Student> cacheList = (List<Student>) cache.get("all-student-local");
        // 如果本地缓存为空则从数据库进行查询
        if (cacheList == null) {
            List<Student> list = this.getDataFromDB();
            cache.put("all-student", list);
            return list;
        }
        return cacheList;
    }

    /*
     * @MethodName: getLocalDataCacheOptimizeFromDB
     * @Description: 此处模拟使用redis缓存，优化的情况下从数据库的查询
     * @Param: []
     * @Return: java.util.List<com.liwei.demo.entity.Student>
     * @Author: LiWei
     * @Date: 2020/5/25
     **/
    private List<Student> getLocalDataCacheOptimizeFromDB() {
        synchronized (this) {
            //此处使用双重检查，进入同步代码块的时候在此判断缓存是否由数据
            String rdisSynValue = redisTemplate.opsForValue().get("all-student-redis");
            if (StringUtils.isEmpty(rdisSynValue)) {
                //此处模拟数据库查询操作
                List<Student> list = new ArrayList<>();
                list.add(new Student("wangwu1", 25, "man"));
                list.add(new Student("wangwu2", 25, "man"));
                list.add(new Student("wangwu3", 25, "man"));
                //查询后在此缓存
                String jsonData = JSON.toJSONString(list);
                redisTemplate.opsForValue().set("all-student-redis", jsonData);
                return list;
            }
            //缓存有数据则返回结果需要由json字符串转换为指定对象
            List<Student> result = JSON.parseObject(rdisSynValue, new TypeReference<List<Student>>() {
            });
            return result;
        }
    }

    /*
     * @MethodName: getLocalDataCacheFromDB
     * @Description: 此处模拟使用redis缓存从数据库的查询
     * @Param: []
     * @Return: java.util.List<com.liwei.demo.entity.Student>
     * @Author: LiWei
     * @Date: 2020/5/25
     **/
    private List<Student> getLocalDataCacheFromDB() {
        List<Student> list = new ArrayList<>();
        list.add(new Student("lisi2", 24, "man"));
        list.add(new Student("lisi2", 24, "man"));
        list.add(new Student("lisi3", 24, "man"));
        return list;
    }

    /*
     * @MethodName: getDataFromDB
     * @Description: 此处模拟数据库的查询操作
     * @Param: []
     * @Return: java.util.List<com.liwei.demo.entity.Student>
     * @Author: LiWei
     * @Date: 2020/5/25
     **/
    private List<Student> getDataFromDB() {
        List<Student> list = new ArrayList<>();
        list.add(new Student("zhangsan1", 23, "man"));
        list.add(new Student("zhangsan2", 24, "man"));
        list.add(new Student("zhangsan3", 25, "man"));
        return list;
    }

}
