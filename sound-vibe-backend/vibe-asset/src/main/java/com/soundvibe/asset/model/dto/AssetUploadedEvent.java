package com.soundvibe.asset.model.dto;

import java.io.Serializable;

/**
 * 资产上传完成事件消息体（不可变 Record）
 * 发送到 RabbitMQ，供 vibe-analysis 服务消费
 *
 * @param assetId     资产数据库主键 ID
 * @param storageName MinIO 存储对象名（含路径前缀）
 * @author SoundVibe Team
 */
public record AssetUploadedEvent(
        Long assetId,
        String storageName
) implements Serializable {
}
