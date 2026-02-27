package com.soundvibe.asset.infrastructure;

import com.soundvibe.asset.config.RabbitMQConfig;
import com.soundvibe.asset.model.dto.AssetUploadedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/**
 * 资产事件发布器
 * 负责将资产生命周期事件发送到 RabbitMQ
 * <p>
 * 职责单一 (SRP): 只关注消息发送，不包含业务逻辑
 *
 * @author SoundVibe Team
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AssetEventPublisher {

    private final RabbitTemplate rabbitTemplate;

    /**
     * 发布「资产上传完成」事件
     * 发送到 Topic Exchange，路由键为 asset.uploaded
     * vibe-analysis 服务消费此消息后执行音频特征提取
     *
     * @param assetId     资产数据库主键
     * @param storageName MinIO 存储对象名
     */
    public void publishUploadedEvent(Long assetId, String storageName) {
        var event = new AssetUploadedEvent(assetId, storageName);
        try {
            rabbitTemplate.convertAndSend(
                    RabbitMQConfig.ASSET_TOPIC_EXCHANGE,
                    RabbitMQConfig.ROUTING_KEY_ASSET_UPLOADED,
                    event
            );
            log.info("资产上传事件已发布: assetId={}, storageName={}", assetId, storageName);
        } catch (Exception e) {
            // MQ 发送失败不影响上传主流程（最终一致性策略）
            log.error("资产上传事件发布失败: assetId={}, storageName={}", assetId, storageName, e);
        }
    }
}
