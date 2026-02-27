package com.soundvibe.asset.infrastructure;

import com.soundvibe.asset.config.MinioProperties;
import com.soundvibe.common.exception.BizException;
import com.soundvibe.common.result.ResultCode;
import io.minio.*;
import io.minio.http.Method;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.concurrent.TimeUnit;

/**
 * MinIO 操作模板类
 * 封装 MinIO Java SDK 的常用操作，提供统一的存储抽象
 * <p>
 * 职责:
 * 1. 启动时确保 Bucket 存在
 * 2. 文件上传（PutObject）
 * 3. 获取预签名 URL
 * 4. 文件删除
 *
 * @author SoundVibe Team
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MinioTemplate {

    private final MinioClient minioClient;
    private final MinioProperties minioProperties;

    /**
     * 预签名 URL 默认有效期（小时）
     */
    private static final int PRESIGNED_URL_EXPIRY_HOURS = 24;

    /**
     * 服务启动时初始化 Bucket
     * 若 Bucket 不存在则自动创建
     */
    @PostConstruct
    public void initBucket() {
        try {
            var bucketName = minioProperties.getBucketName();
            var exists = minioClient.bucketExists(
                    BucketExistsArgs.builder().bucket(bucketName).build()
            );
            if (!exists) {
                minioClient.makeBucket(
                        MakeBucketArgs.builder().bucket(bucketName).build()
                );
                log.info("MinIO Bucket [{}] 创建成功", bucketName);
            } else {
                log.info("MinIO Bucket [{}] 已存在，跳过创建", bucketName);
            }
        } catch (Exception e) {
            log.error("MinIO Bucket 初始化失败", e);
            throw new BizException(ResultCode.SYSTEM_ERROR, "MinIO Bucket 初始化失败: " + e.getMessage());
        }
    }

    /**
     * 上传文件到 MinIO
     *
     * @param objectName  对象名称（含路径前缀，如 audio/2026/02/uuid.mp3）
     * @param inputStream 文件输入流
     * @param size        文件大小（字节）
     * @param contentType 文件 MIME 类型
     */
    public void upload(String objectName, InputStream inputStream, long size, String contentType) {
        try {
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(minioProperties.getBucketName())
                            .object(objectName)
                            .stream(inputStream, size, -1)
                            .contentType(contentType)
                            .build()
            );
            log.info("文件上传成功: bucket={}, object={}, size={}",
                    minioProperties.getBucketName(), objectName, size);
        } catch (Exception e) {
            log.error("文件上传到 MinIO 失败: object={}", objectName, e);
            throw new BizException(ResultCode.FILE_UPLOAD_FAILED, "文件上传失败: " + e.getMessage());
        }
    }

    /**
     * 获取文件的预签名访问 URL
     *
     * @param objectName 对象名称
     * @return 预签名 URL（有效期 24 小时）
     */
    public String getPresignedUrl(String objectName) {
        try {
            return minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .bucket(minioProperties.getBucketName())
                            .object(objectName)
                            .method(Method.GET)
                            .expiry(PRESIGNED_URL_EXPIRY_HOURS, TimeUnit.HOURS)
                            .build()
            );
        } catch (Exception e) {
            log.error("获取预签名 URL 失败: object={}", objectName, e);
            throw new BizException(ResultCode.SYSTEM_ERROR, "获取文件访问链接失败: " + e.getMessage());
        }
    }

    /**
     * 从 MinIO 中读取文件对象（流式）
     * 用于下载场景，调用方负责关闭 InputStream
     *
     * @param objectName 对象名称
     * @return 文件输入流
     */
    public InputStream getObject(String objectName) {
        try {
            return minioClient.getObject(
                    GetObjectArgs.builder()
                            .bucket(minioProperties.getBucketName())
                            .object(objectName)
                            .build()
            );
        } catch (Exception e) {
            log.error("从 MinIO 读取文件失败: object={}", objectName, e);
            throw new BizException(ResultCode.SYSTEM_ERROR, "文件读取失败: " + e.getMessage());
        }
    }

    /**
     * 从 MinIO 中删除文件
     *
     * @param objectName 对象名称
     */
    public void remove(String objectName) {
        try {
            minioClient.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(minioProperties.getBucketName())
                            .object(objectName)
                            .build()
            );
            log.info("文件删除成功: bucket={}, object={}",
                    minioProperties.getBucketName(), objectName);
        } catch (Exception e) {
            log.error("从 MinIO 删除文件失败: object={}", objectName, e);
            throw new BizException(ResultCode.SYSTEM_ERROR, "文件删除失败: " + e.getMessage());
        }
    }
}
