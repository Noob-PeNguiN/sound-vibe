package com.soundvibe.asset.config;

import io.minio.MinioClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MinIO 客户端配置类
 * 初始化 MinioClient Bean，供全局注入使用
 *
 * @author SoundVibe Team
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class MinioConfig {

    private final MinioProperties minioProperties;

    /**
     * 创建 MinioClient 实例
     *
     * @return MinioClient Bean
     */
    @Bean
    public MinioClient minioClient() {
        log.info("初始化 MinIO 客户端: endpoint={}, bucket={}",
                minioProperties.getEndpoint(), minioProperties.getBucketName());

        return MinioClient.builder()
                .endpoint(minioProperties.getEndpoint())
                .credentials(minioProperties.getAccessKey(), minioProperties.getSecretKey())
                .build();
    }
}
