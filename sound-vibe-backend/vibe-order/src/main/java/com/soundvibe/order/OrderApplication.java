package com.soundvibe.order;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * SoundVibe 订单管理服务启动类
 * 负责购物车、下单结算、订单超时取消等功能
 *
 * @author SoundVibe Team
 */
@SpringBootApplication(scanBasePackages = {"com.soundvibe.order", "com.soundvibe.common"})
@EnableDiscoveryClient
@EnableFeignClients(basePackages = "com.soundvibe.order.feign")
@MapperScan("com.soundvibe.order.mapper")
public class OrderApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrderApplication.class, args);
        System.out.println("""
                ========================================
                   SoundVibe Order Service Started!
                   Port: 8085
                   Nacos: localhost:8848
                ========================================
                """);
    }
}
