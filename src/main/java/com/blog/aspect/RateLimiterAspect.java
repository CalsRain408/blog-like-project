package com.blog.aspect;

import com.blog.annotation.RateLimiter;
import com.blog.dto.UserDTO;
import com.blog.exception.RateLimiterException;
import com.blog.utils.RedisConstants;
import com.blog.utils.UserHolder;
import io.reactivex.Single;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;
import java.util.Collections;

@Aspect
@Component
@Slf4j
public class RateLimiterAspect {
    @Resource
    private RedisTemplate redisTemplate;

    // 限流Lua script
    private static final DefaultRedisScript<Long> SLIDING_WINDOW_SCRIPT;

    static {
        SLIDING_WINDOW_SCRIPT = new DefaultRedisScript<>();
        SLIDING_WINDOW_SCRIPT.setLocation(new ClassPathResource("limiter.lua"));
        SLIDING_WINDOW_SCRIPT.setResultType(Long.class);
    }

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Before("@annotation(rateLimiter)")
    public void doBefore(JoinPoint point, RateLimiter rateLimiter) {
        log.info("切面拦截");

        // 获取注解上的参数
        String key = rateLimiter.key();
        long window = rateLimiter.window();
        long limit = rateLimiter.limit();

        // 构建完整的限流key
        String fullKey = buildRateLimitKey(point, rateLimiter, key);
        // 执行限流脚本
        Long result = executeSlidingWindowScript(fullKey, window, limit);

        // 如果返回0表示被限流
        if (result != null && result == 0) {
            throw new RateLimiterException(rateLimiter.message());
        }
    }

    /**
     * 执行滑动窗口限流脚本
     * @param fullKey 限流key
     * @param window 时间窗口（秒）
     * @param limit 限制请求数量
     * @return 当前窗口内请求数量计数（如果被限流返回0）
     */
    private Long executeSlidingWindowScript(String fullKey, long window, long limit) {
        long now = System.currentTimeMillis();
        log.info("key:{}, window:{}, limit:{}", fullKey, window, limit);

        return stringRedisTemplate.execute(
                SLIDING_WINDOW_SCRIPT,
                Collections.singletonList(fullKey),
                String.valueOf(window),
                String.valueOf(limit),
                String.valueOf(now)
        );
    }

    /**
     * 构建完整限流key
     * @param point
     * @param rateLimiter
     * @param key
     * @return
     */
    private String buildRateLimitKey(JoinPoint point, RateLimiter rateLimiter, String key) {
        StringBuilder keyBuilder = new StringBuilder(RedisConstants.SLIDE_WINDOW_KEY);  // 前缀

        MethodSignature signature = (MethodSignature) point.getSignature();
        Method method = signature.getMethod();

        // 添加类名和方法名
        keyBuilder.append(method.getDeclaringClass().getName())
                .append(":")
                .append(method.getName());

        // 根据限流类型添加额外维度
        switch (rateLimiter.type()) {
            case IP:
                keyBuilder.append(":ip:").append(getClientIp());
                break;
            case USER:
                keyBuilder.append(":user:").append(getCurrentUserId());
                break;
            case METHOD:
            default:
                // 方法级限流使用默认key
                break;
        }

        return keyBuilder.toString();
    }

    /**
     * 获取客户端IP
     */
    private String getClientIp() {
        HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes()).getRequest();
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("WL-Proxy-Client-IP");
        }
        if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        return ip;
    }

    /**
     * 获取当前用户ID（需要根据实际系统实现）
     */
    private String getCurrentUserId() {
        // 从ThreadLocal中获取用户id
        UserDTO userDTO = UserHolder.getUser();

        if (userDTO != null){
            return userDTO.getId().toString();
        }

        return "anonymous"; // 默认返回匿名用户
    }
}
