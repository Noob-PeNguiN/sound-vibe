package com.soundvibe.gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

/**
 * Gateway 全局 CORS 跨域配置
 * <p>
 * ⚠️ 注意: Gateway 使用 WebFlux 响应式栈，因此必须使用
 * {@link org.springframework.web.cors.reactive.CorsWebFilter}
 * 而非 Servlet 栈的 {@link org.springframework.web.filter.CorsFilter}。
 * <p>
 * 当所有请求都通过 Gateway 转发后，下游服务（vibe-auth/vibe-asset/vibe-catalog）
 * 可以移除各自的 CorsConfig，由 Gateway 统一处理跨域。
 *
 * @author SoundVibe Team
 */
@Configuration
public class CorsConfig {

    @Bean
    public CorsWebFilter corsWebFilter() {
        CorsConfiguration config = new CorsConfiguration();

        // 允许的前端地址
        config.addAllowedOriginPattern("http://localhost:5173");
        config.addAllowedOriginPattern("http://127.0.0.1:5173");

        // 允许的请求方法
        config.addAllowedMethod(HttpMethod.GET);
        config.addAllowedMethod(HttpMethod.POST);
        config.addAllowedMethod(HttpMethod.PUT);
        config.addAllowedMethod(HttpMethod.DELETE);
        config.addAllowedMethod(HttpMethod.OPTIONS);

        // 允许所有请求头
        config.addAllowedHeader("*");

        // 允许携带认证信息 (Cookie、Authorization Header)
        config.setAllowCredentials(true);

        // 暴露自定义 Header 给前端（如 X-User-Id）
        config.addExposedHeader("X-User-Id");
        config.addExposedHeader("X-User-Role");

        // 预检请求缓存时间 (秒)
        config.setMaxAge(3600L);

        // 注册到所有路径
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);

        return new CorsWebFilter(source);
    }
}
