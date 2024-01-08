package com.cny.aop;

import lombok.extern.slf4j.Slf4j;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.recipes.locks.InterProcessMutex;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.servlet.mvc.condition.RequestConditionHolder;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.PrintWriter;
import java.util.concurrent.TimeUnit;

/**
 * @author : chennengyuan
 */
@Slf4j
@Component
@Aspect
public class IdempotentAspect {

    @Autowired
    private RedisTemplate redisTemplate;
    @Autowired
    private CuratorFramework client;

    @Pointcut("@annotation(com.cny.annotation.Idempotent)")
    public void point(){}

    @Before("point()")
    public void before(JoinPoint joinPoint) throws Exception {
        Signature signature = joinPoint.getSignature();
        Object target = joinPoint.getTarget();
        Object[] args = joinPoint.getArgs();
        String name = signature.getName();

        ServletRequestAttributes requestAttributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        HttpServletRequest request= requestAttributes.getRequest();
        HttpServletResponse response = requestAttributes.getResponse();

        String addressTokenKey = "USER_ADDRESS_TOKEN_" + request.getSession().getId();
        String addressLockKey = "/USER_ADDRESS_LOCK" + request.getSession().getId();
        String token = request.getParameter("token");

        InterProcessMutex lock = new InterProcessMutex(client, addressLockKey);
        try {
            if (lock.acquire(60, TimeUnit.SECONDS)) {
                //1.获取到redis中保存的key
                String addressTokenInRedis = (String) redisTemplate.opsForValue().get(addressTokenKey);
                if (StringUtils.isEmpty(addressTokenInRedis) || !addressTokenInRedis.equals(token)) {
                    throw new Exception("token有误");
                }

                //3.删除redis中的token
                redisTemplate.delete(addressTokenKey);

            }

            //joinPoint.proceed();

        } catch (Exception e) {
            log.error("校验token出现异常：{}", e.getMessage());
            throw new Exception(e.getMessage());
        } catch (Throwable e) {
            throw new RuntimeException(e);
        } finally {
            try {
                lock.release();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

}
