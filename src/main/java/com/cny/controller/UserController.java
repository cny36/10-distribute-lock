package com.cny.controller;

import com.cny.annotation.Idempotent;
import lombok.extern.slf4j.Slf4j;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.recipes.locks.InterProcessMutex;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * @author : chennengyuan
 * 接口幂等性实现
 */
@Slf4j
@RestController
@RequestMapping("/user")
public class UserController {

    @Autowired
    private RedisTemplate redisTemplate;

    @Autowired
    private CuratorFramework client;

    /**
     * 获取token，保存到redis中
     *
     * @param request
     * @return
     */
    @GetMapping("/getToken")
    public String getToken(HttpServletRequest request) {
        String token = UUID.randomUUID().toString();
        redisTemplate.opsForValue().set("USER_ADDRESS_TOKEN_" + request.getSession().getId(), token, 30, TimeUnit.MINUTES);
        return token;
    }

    /**
     * 创建用户地址时，携带token实现接口幂等
     *
     * @param token
     * @param address
     * @param request
     * @return
     */
    @PostMapping("/createAddress")
    public String createAddress(String token, String address, HttpServletRequest request) throws Exception {
        String addressTokenKey = "USER_ADDRESS_TOKEN_" + request.getSession().getId();
        String addressLockKey = "/USER_ADDRESS_LOCK" + request.getSession().getId();

        InterProcessMutex lock = new InterProcessMutex(client, addressLockKey);
        try {
            if (lock.acquire(60, TimeUnit.SECONDS)) {
                //1.获取到redis中保存的key
                String addressTokenInRedis = (String) redisTemplate.opsForValue().get(addressTokenKey);
                if (StringUtils.isEmpty(addressTokenInRedis)) {
                    throw new Exception("addressToken 不存在");
                }
                if (!addressTokenInRedis.equals(token)) {
                    throw new Exception("addressToken 不匹配");
                }

                //2.处理具体业务逻辑
                log.info("模拟业务逻辑处理 保存地址信息成功 success");

                //3.删除redis中的token
                redisTemplate.delete(addressTokenKey);

                return "success";
            }
        } catch (Exception e) {
            log.error("创建用户地址出现异常：{}", e.getMessage());
        } finally {
            lock.release();
        }

        return "faild";
    }


    /**
     * 基于Aop或拦截器实现接口幂等性
     *
     * @param address
     * @return
     */
    @PostMapping("/createAddress2")
    @Idempotent
    public String createAddress2(String address) {
        //2.处理具体业务逻辑
        log.info("模拟业务逻辑处理 保存地址信息成功 success");
        return "success";
    }


}
