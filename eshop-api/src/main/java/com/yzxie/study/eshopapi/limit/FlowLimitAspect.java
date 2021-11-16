package com.yzxie.study.eshopapi.limit;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.util.concurrent.RateLimiter;
import com.yzxie.study.eshopcommon.exception.ApiException;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.annotation.PostConstruct;
import javax.servlet.http.HttpServletRequest;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Author: xieyizun
 * Version: 1.0
 * Date: 2019-08-25
 * Description: 当前部署实例的全局限流
 **/
@Aspect
@Component
@Order(1)
// Order序号越小，优先级越高
public class FlowLimitAspect {
    /**
     * url维度限流
     */
    private Map<String, RateLimiter> uriLimiterMap = new HashMap<>();

    /**
     * 用户维度限流
     */
    private LoadingCache<String, RateLimiter> userLimiterMap;

    @Autowired
    private Environment environment;

    /**
     * 用户每秒可发送请求数
     */
    private String uuidLimit;
    /**
     * 需要限流的uri
     */
    private String uriList;
    
    private String uriLimit;

    @PostConstruct
    public void init() {
        this.uuidLimit = environment.getProperty("flow.uuid.limit");
        this.uriList = environment.getProperty("flow.uris");
        this.uriLimit = environment.getProperty("flow.uri.limit");
        // 初始化uri的limiter
        if (uriList != null) {
            String[] uris = uriList.split(",");
            for (String uri : uris) {
                // 每个uri每秒最多接收20个请求，也可以优化为每个uri不一样
                uriLimiterMap.put(uri, RateLimiter.create(Integer.valueOf(uriLimit)));
            }
        }
        // 初始化uuid的limiter
        userLimiterMap = CacheBuilder.newBuilder()
                .maximumSize(10000)
                .expireAfterWrite(1, TimeUnit.HOURS)
                .build(new CacheLoader<String, RateLimiter>() {
                    @Override
                    public RateLimiter load(String s) throws Exception {
                        // 每个新的uuid，每秒只发出 uuidLimit 个令牌，即每秒只能发送 uuidLimit 个请求
                        return RateLimiter.create(Integer.valueOf(uuidLimit));
                    }
                });
    }

    @Pointcut("@annotation(com.yzxie.study.eshopapi.limit.FlowLimit)")
    public void flowLimitAspect() {}

    @Around("flowLimitAspect()")
    public Object limit(ProceedingJoinPoint proceedingJoinPoint) throws Throwable {
        Method method = ((MethodSignature)proceedingJoinPoint.getSignature()).getMethod();
        FlowLimit flowLimit = method.getAnnotation(FlowLimit.class);
        if (flowLimit != null) {
            HttpServletRequest request = getCurrentRequest();
            String uri = request.getRequestURI();
            String userId = request.getHeader("userId");
            RateLimiter uriLimiter = uriLimiterMap.get(uri);
            // uri：url维度限流
            if (uriLimiter != null) {
                boolean allow = uriLimiter.tryAcquire();
                if (!allow) {
                    throw new ApiException("抢购人数太多，请稍后再试");
                }
            }
            // useId：用户维度限流
            if (userId != null) {
                RateLimiter userLimiter = userLimiterMap.get(userId);
                boolean allow = userLimiter.tryAcquire();
                if (!allow) {
                    throw new ApiException("请求次数太多，请稍后再试");
                }
            }
        }

        // 继续执行
        Object result = proceedingJoinPoint.proceed();
        return result;
    }

    private HttpServletRequest getCurrentRequest() {
        HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes()).getRequest();
        return request;
    }
}
