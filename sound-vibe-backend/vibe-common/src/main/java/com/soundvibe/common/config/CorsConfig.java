package com.soundvibe.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

/**
 * 跨域配置
 * 允许前端（localhost:5173）访问后端 API
 *
 * @author SoundVibe Team
 */
@Configuration
public class CorsConfig {

    @Bean
    public CorsFilter corsFilter() {
        CorsConfiguration config = new CorsConfiguration();
        
        // 允许的前端地址
        config.addAllowedOriginPattern("http://localhost:5173");
        config.addAllowedOriginPattern("http://127.0.0.1:5173");
        
        // 允许所有请求头
        config.addAllowedHeader("*");
        
        // 允许所有请求方法 (GET, POST, PUT, DELETE 等)
        config.addAllowedMethod("*");
        
        // 允许携带认证信息 (Cookie、Authorization Header)
        config.setAllowCredentials(true);
        
        // 预检请求的有效期 (秒)
        config.setMaxAge(3600L);
        
        // 注册跨域配置到所有路径
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        
        return new CorsFilter(source);
    }
}
