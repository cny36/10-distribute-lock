package com.cny.inteceptor;

import com.cny.annotation.Idempotent;
import lombok.extern.slf4j.Slf4j;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.recipes.locks.InterProcessMutex;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.PrintWriter;
import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;

/**
 * @author : chennengyuan
 * 接口幂等性校验拦截器
 */
@Slf4j
@Component
public class IdempotentInteceptor implements HandlerInterceptor {

    @Autowired
    private RedisTemplate redisTemplate;
    @Autowired
    private CuratorFramework client;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        HandlerMethod handlerMethod = (HandlerMethod) handler;
        Method method = handlerMethod.getMethod();
        //注解存在才需要校验
        boolean annotationPresent = method.isAnnotationPresent(Idempotent.class);
        if (annotationPresent) {
            String addressTokenKey = "USER_ADDRESS_TOKEN_" + request.getSession().getId();
            String addressLockKey = "/USER_ADDRESS_LOCK" + request.getSession().getId();
            String token = request.getParameter("token");

            InterProcessMutex lock = new InterProcessMutex(client, addressLockKey);
            try {
                if (lock.acquire(60, TimeUnit.SECONDS)) {
                    //1.获取到redis中保存的key
                    String addressTokenInRedis = (String) redisTemplate.opsForValue().get(addressTokenKey);
                    if (StringUtils.isEmpty(addressTokenInRedis) || !addressTokenInRedis.equals(token)) {
                        response.setContentType("application/json;charset=utf-8");
                        PrintWriter writer = response.getWriter();
                        writer.print("token有误");
                        writer.flush();
                        writer.close();
                        return false;
                    }

                    //3.删除redis中的token
                    redisTemplate.delete(addressTokenKey);

                    return true;
                }
            } catch (Exception e) {
                log.error("校验token出现异常：{}", e.getMessage());
            } finally {
                lock.release();
            }
        }

        return HandlerInterceptor.super.preHandle(request, response, handler);
    }
}
