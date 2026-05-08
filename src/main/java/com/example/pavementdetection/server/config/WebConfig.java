package com.example.pavementdetection.server.config;

import com.example.pavementdetection.server.interceptor.LoginInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.*;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new LoginInterceptor())
                .addPathPatterns("/**")           // 拦截所有路径
                .excludePathPatterns(
                        "/login",                     // 登录页
                        "/register",                  // 注册接口（POST）
                        "/css/**", "/js/**",           // 静态资源
                        "/api/detection/upload"        // APP上传接口不拦截！
                );
    }
}