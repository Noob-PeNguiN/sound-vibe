package com.soundvibe.asset.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * MinIO 配置属性类
 * 绑定 application.yml 中 minio.* 配置项
 *
 * @author SoundVibe Team
 */
@Data
@Component
@ConfigurationProperties(prefix = "minio")
public class MinioProperties {

    /**
     * MinIO 服务端点地址
     */
    private String endpoint;

    /**
     * 访问密钥
     */
    private String accessKey;

    /**
     * 密钥凭证
     */
    private String secretKey;

    /**
     * 默认桶名称
     */
    private String bucketName;
}
