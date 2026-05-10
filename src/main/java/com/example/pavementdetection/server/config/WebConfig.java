package com.example.pavementdetection.server.config;


import com.example.pavementdetection.server.interceptor.AppTokenInterceptor;
import com.example.pavementdetection.server.interceptor.LoginInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;


@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Autowired private LoginInterceptor loginInterceptor;
    @Autowired
    private AppTokenInterceptor appTokenInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {

        // ① Web 管理平台拦截器（只管页面路由，不管 /api/**）
        registry.addInterceptor(loginInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns(
                        "/login", "/register", "/logout",
                        "/api/**",        // ← 所有 API 请求交给 AppTokenInterceptor
                        "/images/**",
                        "/css/**", "/js/**", "/favicon.ico"
                );

        // ② APP Token 拦截器（拦截所有检测接口，包括 upload）
        registry.addInterceptor(appTokenInterceptor)
                .addPathPatterns("/api/detection/**")
                .excludePathPatterns("/api/auth/**");
    }
}