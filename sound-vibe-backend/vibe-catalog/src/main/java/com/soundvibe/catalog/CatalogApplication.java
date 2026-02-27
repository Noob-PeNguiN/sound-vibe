package com.soundvibe.catalog;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
/**
 * SoundVibe 目录管理服务启动类
 * 负责音乐作品（Beat/Track）的发布、查询、元数据管理
 *
 * @author SoundVibe Team
 */
@SpringBootApplication(scanBasePackages = {"com.soundvibe.catalog", "com.soundvibe.common"})
@EnableDiscoveryClient
@MapperScan("com.soundvibe.catalog.mapper")
public class CatalogApplication {

    public static void main(String[] args) {
        SpringApplication.run(CatalogApplication.class, args);
        System.out.println("""
                ========================================
                   SoundVibe Catalog Service Started!
                   Port: 8083
                   Nacos: localhost:8848
                ========================================
                """);
    }
}
