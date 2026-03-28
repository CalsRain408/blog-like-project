package com.blog.interceptor;

import com.blog.dto.UserDTO;
import com.blog.entity.User;
import com.blog.utils.SystemConstants;
import com.blog.utils.UserHolder;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.BeanUtils;
import org.springframework.web.servlet.HandlerInterceptor;

public class LoginInterceptor implements HandlerInterceptor {
    /**
     * 前置拦截器判断用户是否登陆
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {

        // 直接从ThreadLocal中判断用户是否登陆
        UserDTO user = UserHolder.getUser();

        if (user == null) {
            // 用户不存在，拦截
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return false;
        }

        // 2. 用户存在，放行
        return true;
    }
}
