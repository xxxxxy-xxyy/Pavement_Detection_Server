package com.example.pavementdetection.server.interceptor;

import com.example.pavementdetection.server.util.JwtUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Map;

@Component
public class AppTokenInterceptor implements HandlerInterceptor {

    @Autowired private JwtUtil jwtUtil;

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) throws Exception {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);
            String username = jwtUtil.parseUsername(token);
            if (username != null) {
                // 把 username 存到 request attribute，Controller 里可以取
                request.setAttribute("appUser", username);
                return true;
            }
        }
        response.setStatus(401);
        response.setContentType("application/json;charset=UTF-8");
        new ObjectMapper().writeValue(response.getWriter(),
                Map.of("success", false, "message", "未登录或Token已过期"));
        return false;
    }
}