package com.soundvibe.catalog.listener;

import com.soundvibe.catalog.config.RabbitMQConfig;
import com.soundvibe.catalog.service.TrackService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 音频分析完成消息监听器
 * 消费 Python vibe-analysis worker 发送的 asset.analysis.completed 事件
 * <p>
 * 触发时机：Python 服务完成 BPM/调性分析 + CLAP 嵌入向量计算后
 * 处理逻辑：找到引用该 asset 的所有已上架 track，重新同步到 ES（携带最新的 audio_vector）
 *
 * @author SoundVibe Team
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AssetAnalysisCompletedListener {

    private final TrackService trackService;

    @RabbitListener(queues = RabbitMQConfig.ANALYSIS_COMPLETED_QUEUE)
    public void onAnalysisCompleted(Map<String, Object> message) {
        Long assetId = null;
        try {
            Object rawId = message.get("assetId");
            if (rawId == null) {
                log.warn("收到无效的分析完成消息（缺少 assetId）: {}", message);
                return;
            }
            assetId = rawId instanceof Number ? ((Number) rawId).longValue() : Long.parseLong(rawId.toString());

            log.info("收到分析完成通知: assetId={}", assetId);
            int count = trackService.resyncTracksByAssetId(assetId);
            log.info("分析完成处理结果: assetId={}, 重新同步 {} 条 track", assetId, count);
        } catch (Exception e) {
            log.error("处理分析完成消息失败: assetId={}, error={}", assetId, e.getMessage(), e);
        }
    }
}
