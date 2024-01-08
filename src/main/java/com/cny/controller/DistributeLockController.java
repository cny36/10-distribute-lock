package com.cny.controller;

import lombok.extern.slf4j.Slf4j;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.recipes.locks.InterProcessMutex;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * @author : chennengyuan
 * 分布式锁的实现 （Redis、Zookeeper）
 */
@Slf4j
@RestController
@RequestMapping
public class DistributeLockController {

    /**
     * SpringDataRedis 客户端
     */
    @Resource(name = "myRedisTemplate")
    private RedisTemplate<String, String> redisTemplate;

    /**
     * Redission 客户端
     */
    @Autowired
    private RedissonClient redissonClient;

    /**
     * Zookeeper 客户端
     */
    @Autowired
    private CuratorFramework zookeeperClient;

    /**
     * 基于Zookeeper的客户端Curator实现分布式锁
     *
     * @return
     */
    @GetMapping("/curatorLock")
    public String curatorLock() {
        InterProcessMutex lock = new InterProcessMutex(zookeeperClient, "/user");
        try {
            if (lock.acquire(6, TimeUnit.SECONDS)) {
                log.info("获取到锁成功 - {}", Thread.currentThread().getName());
                try {
                    log.info("开始执行具体的业务逻辑处理 - {}", Thread.currentThread().getName());
                    Thread.sleep(12000);
                    log.info("业务逻辑处理完毕，准备释放锁 - {}", Thread.currentThread().getName());
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                return "success";
            }
        } catch (Exception e) {

        } finally {
            try {
                lock.release();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return "fail";
    }


    /**
     * 基于原生Zookeeper实现分布式锁
     *
     * @return
     */
    @RequestMapping("/zkLock")
    public String zkLock() {
        try (ZkDistirbuteLock lock = new ZkDistirbuteLock()) {
            if (lock.lock("order")) {
                log.info("获取锁成功 - {}", Thread.currentThread().getName());
                log.info("开始处理业务逻辑 - {}", Thread.currentThread().getName());
                Thread.sleep(30000);
                log.info("结束处理业务逻辑 - {}", Thread.currentThread().getName());
            }
        } catch (Exception e) {
            log.error(e.getMessage());
        }
        return "success";
    }

    /**
     * 基于 Redisson 实现分布式锁
     *
     * @return
     */
    @RequestMapping("/redissonLock")
    public String redissonLock() {
        //获取到锁对象
        RLock lock = redissonClient.getLock("6666");

        try {
            //尝试获取锁
            lock.lock();
            log.info("获取到锁成功 - {}", Thread.currentThread().getId());
            log.info("处理业务逻辑中..... - {}", Thread.currentThread().getId());
            Thread.sleep(10000);
            log.info("业务逻辑处理完成 - {}", Thread.currentThread().getId());
            return "SUCCESS";
        } catch (Exception e) {
            e.getStackTrace();
        } finally {
            //释放锁
            lock.unlock();
            log.info("释放锁成功 - {}", Thread.currentThread().getId());
        }
        return "FAIL";
    }


    /**
     * 基于 RedisTemplate 实现分布式锁
     *
     * @return
     */
    @RequestMapping("/redisLock")
    public String redisLock() {
        //生成一个UUID作为value值
        String key = "6666";
        String value = UUID.randomUUID().toString();

        //尝试获取到分布式锁
        Boolean result = redisTemplate.opsForValue().setIfAbsent(key, value, 3000, TimeUnit.SECONDS);

        //根据获取的结果做处理
        if (Boolean.TRUE.equals(result)) {
            log.info("获取锁成功 - {}", Thread.currentThread().getName());
            try {
                log.info("开始处理业务逻辑 - {}", Thread.currentThread().getName());
                Thread.sleep(12000);
                log.info("结束处理业务逻辑 - {}", Thread.currentThread().getName());
            } catch (InterruptedException e) {
                e.printStackTrace();
            } finally {
                String script = "if redis.call(\"get\",KEYS[1]) == ARGV[1] then\n" +
                        " return redis.call(\"del\",KEYS[1])\n" +
                        "else\n" +
                        " return 0\n" +
                        "end";
                Boolean executeResult = redisTemplate.execute(RedisScript.of(script, Boolean.class), Collections.singletonList(key), value);
                log.info("解锁结果为:{} - {}", executeResult, Thread.currentThread().getName());
            }
            return "SUCCESS";
        }
        log.info("获取锁失败 - {}", Thread.currentThread().getName());
        return "FAIL";
    }
}
