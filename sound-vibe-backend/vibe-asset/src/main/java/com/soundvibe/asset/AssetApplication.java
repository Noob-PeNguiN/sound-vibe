package com.soundvibe.asset;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * SoundVibe 数字资产管理服务启动类
 * 负责音频文件上传、MinIO 存储、资产元数据管理
 *
 * @author SoundVibe Team
 */
@SpringBootApplication(scanBasePackages = {"com.soundvibe.asset", "com.soundvibe.common"})
@EnableDiscoveryClient
@MapperScan("com.soundvibe.asset.mapper")
public class AssetApplication {

    public static void main(String[] args) {
        SpringApplication.run(AssetApplication.class, args);
        System.out.println("""
                ========================================
                   SoundVibe Asset Service Started!
                   Port: 8082
                   Nacos: localhost:8848
                   MinIO: localhost:9000
                ========================================
                """);
    }
}
