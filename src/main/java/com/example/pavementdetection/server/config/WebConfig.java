package com.example.pavementdetection.server.config;

import com.example.pavementdetection.server.interceptor.AppTokenInterceptor;
import com.example.pavementdetection.server.interceptor.LoginInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Autowired
    private LoginInterceptor loginInterceptor;

    @Autowired
    private AppTokenInterceptor appTokenInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {

        // Web Session 拦截器：只管页面路由，排除所有 /api/**
        registry.addInterceptor(loginInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns(
                        "/login", "/register",
                        "/api/**",          // ← 关键：排除全部 API
                        "/images/**",
                        "/css/**", "/js/**", "/favicon.ico"
                );

        // APP Token 拦截器：只拦真正需要 Token 的接口
        registry.addInterceptor(appTokenInterceptor)
                .addPathPatterns(
                        "/api/detection/upload",          // APP 上传
                        "/api/auth/changePassword",       // 改密
                        "/api/auth/account"               // 注销
                )
                .excludePathPatterns(
                        "/api/auth/register",
                        "/api/auth/login"
                );
    }
}