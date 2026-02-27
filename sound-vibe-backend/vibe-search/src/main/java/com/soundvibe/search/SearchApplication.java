package com.soundvibe.search;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * SoundVibe 搜索服务启动类
 * 基于 Elasticsearch 提供作品元数据搜索能力
 * 通过 RabbitMQ 监听作品变更事件，自动同步索引
 *
 * @author SoundVibe Team
 */
@SpringBootApplication(scanBasePackages = {
        "com.soundvibe.search",
        "com.soundvibe.common.exception"
})
@EnableDiscoveryClient
@EnableFeignClients(basePackages = "com.soundvibe.search.client")
public class SearchApplication {

    public static void main(String[] args) {
        SpringApplication.run(SearchApplication.class, args);
        System.out.println("""
                ========================================
                   SoundVibe Search Service Started!
                   Port: 8084
                   Elasticsearch: localhost:9200
                   RabbitMQ: localhost:5672
                   Nacos: localhost:8848
                ========================================
                """);
    }
}
