package com.blog.interceptor;

import cn.hutool.core.bean.BeanUtil;
import com.blog.dto.UserDTO;
import com.blog.utils.RedisConstants;
import com.blog.utils.UserHolder;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Map;
import java.util.concurrent.TimeUnit;

public class RefreshTokenInterceptor implements HandlerInterceptor {

    // new出来的对象是无法直接注入IOC容器的（拦截器中 XXInterceptor 都是直接new出来的）
    // 所以这里需要在配置类中注入，然后通过构造器传入到当前类中
    private StringRedisTemplate stringRedisTemplate;

    public RefreshTokenInterceptor(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    /**
     * 这个拦截器拦截所有路径
     * 如果用户已经登陆（保持活跃状态），不论发送的是什么请求，刷新Redis中的TTL
     * @param request
     * @param response
     * @param handler
     * @return
     * @throws Exception
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String token = request.getHeader("authorization");
        if (token == null || token.isEmpty()) {
            // 用户未登录，直接放行
            return true;
        }

        // 在Redis中查询用户
        String tokenKey = RedisConstants.LOGIN_USER_KEY + token;
        Map<Object, Object> userMap = stringRedisTemplate.opsForHash().entries(tokenKey);
        if (userMap.isEmpty()) {
            return true;
        }

        UserDTO userDTO = BeanUtil.fillBeanWithMap(userMap, new UserDTO(), false);
        UserHolder.saveUser(userDTO);

        // 刷新tokenKey的TTL
        stringRedisTemplate.expire(tokenKey, RedisConstants.LOGIN_USER_TTL, TimeUnit.MINUTES);

        // 放行
        return true;
    }
}
