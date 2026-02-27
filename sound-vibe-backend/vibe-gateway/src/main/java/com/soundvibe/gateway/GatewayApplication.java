package com.soundvibe.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * SoundVibe API 网关启动类
 * <p>
 * 基于 Spring Cloud Gateway (WebFlux + Netty)，作为微服务集群的统一入口。
 * <ul>
 *   <li>端口: 8080</li>
 *   <li>职责: 路由转发、JWT 鉴权、CORS 处理、负载均衡</li>
 *   <li>注册中心: Nacos (localhost:8848)</li>
 * </ul>
 *
 * @author SoundVibe Team
 */
@SpringBootApplication
@EnableDiscoveryClient
public class GatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }
}
