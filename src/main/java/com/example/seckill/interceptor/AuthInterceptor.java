package com.example.seckill.interceptor;

import com.example.seckill.common.BusinessException;
import com.example.seckill.common.ResultCode;
import com.example.seckill.common.UserContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
@RequiredArgsConstructor
public class AuthInterceptor implements HandlerInterceptor {

    private final StringRedisTemplate redisTemplate;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String uri = request.getRequestURI();

        // 非 API 路径（首页、静态资源）直接放行
        if (!uri.startsWith("/api")) {
            return true;
        }

        String method = request.getMethod();
        if (isPublic(method, uri)) {
            return true;
        }

        String token = request.getHeader("Authorization");
        if (token != null && token.startsWith("Bearer ")) {
            token = token.substring(7);
        }
        if (token == null || token.isBlank()) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        String value = redisTemplate.opsForValue().get("user:token:" + token);
        if (value == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        String[] parts = value.split("\\|");
        Long userId = Long.valueOf(parts[0]);
        String role = parts.length > 1 ? parts[1] : "USER";
        UserContext.set(userId, role);
        return true;
    }

    private boolean isPublic(String method, String uri) {
        if ("POST".equals(method)) {
            return uri.equals("/api/auth/register") || uri.equals("/api/auth/login");
        }
        if ("GET".equals(method)) {
            return uri.matches("^/api/goods/\\d+$")
                    || uri.equals("/api/seckill/activities")
                    || uri.matches("^/api/seckill/activities/\\d+$");
        }
        return false;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        UserContext.clear();
    }
}
