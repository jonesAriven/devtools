package com.jones.activation.config;

import com.jones.activation.entity.AdminUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class AuthInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(AuthInterceptor.class);

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // OPTIONS请求直接放行（CORS预检）
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        HttpSession session = request.getSession(false);
        if (session != null) {
            AdminUser user = (AdminUser) session.getAttribute("loginUser");
            if (user != null) {
                return true;
            }
        }

        // 未登录：API请求返回401，页面请求重定向到登录页
        String accept = request.getHeader("Accept");
        String uri = request.getRequestURI();

        if (uri.startsWith("/activecode/api/")) {
            response.setStatus(401);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"success\":false,\"message\":\"未登录或会话已过期\"}");
            return false;
        }

        // 页面请求重定向到登录页
        response.sendRedirect("/activecode/login.html");
        return false;
    }
}
