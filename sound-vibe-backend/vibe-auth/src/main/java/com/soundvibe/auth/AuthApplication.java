package com.soundvibe.auth;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * SoundVibe 用户认证服务启动类
 * 负责用户注册、登录、JWT 生成与验证
 *
 * @author SoundVibe Team
 */
@SpringBootApplication(scanBasePackages = {"com.soundvibe.auth", "com.soundvibe.common"})
@EnableDiscoveryClient
@MapperScan("com.soundvibe.auth.mapper")
public class AuthApplication {

    public static void main(String[] args) {
        SpringApplication.run(AuthApplication.class, args);
        System.out.println("""
                ========================================
                   SoundVibe Auth Service Started!
                   Port: 8081
                   Nacos: localhost:8848
                ========================================
                """
                    );
    }
}
