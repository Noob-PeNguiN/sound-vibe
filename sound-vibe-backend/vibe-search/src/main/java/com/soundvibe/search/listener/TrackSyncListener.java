package com.soundvibe.search.listener;

import com.soundvibe.search.config.RabbitMQConfig;
import com.soundvibe.search.document.TrackDoc;
import com.soundvibe.search.repository.TrackDocRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 作品同步消息监听器
 * 消费 RabbitMQ 中的作品变更事件，将数据同步到 Elasticsearch
 * <p>
 * v2 重构：
 * - 支持数组字段：bpmValues, musicalKeys, durations
 * - 消息格式由 vibe-catalog 的 TrackServiceImpl.sendTrackSyncMessage 定义
 * - 同步策略：全量覆盖（save = upsert），保证最终一致性
 *
 * @author SoundVibe Team
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TrackSyncListener {

    private final TrackDocRepository trackDocRepository;

    /**
     * 监听作品同步队列
     * 接收 Track 同步消息 → 转换为 TrackDoc → 写入 ES
     *
     * @param message 作品同步消息（Jackson 反序列化为 Map）
     */
    @RabbitListener(queues = RabbitMQConfig.TRACK_SYNC_QUEUE)
    public void onTrackSync(Map<String, Object> message) {
        try {
            var trackDoc = convertToTrackDoc(message);
            trackDocRepository.save(trackDoc);
            log.info("ES 索引同步成功: trackId={}, title={}, bpmValues={}, musicalKeys={}",
                    trackDoc.getId(), trackDoc.getTitle(),
                    trackDoc.getBpmValues(), trackDoc.getMusicalKeys());
        } catch (Exception e) {
            log.error("作品索引同步失败: message={}, error={}", message, e.getMessage(), e);
        }
    }

    /**
     * 将消息 Map 转换为 TrackDoc
     * 消息字段来源于 TrackServiceImpl.sendTrackSyncMessage
     */
    private TrackDoc convertToTrackDoc(Map<String, Object> message) {
        return TrackDoc.builder()
                .id(toLong(message.get("id")))
                .title((String) message.get("title"))
                .trackType((String) message.get("trackType"))
                .producerId(toLong(message.get("producerId")))
                .producerName((String) message.get("producerName"))
                .coverId(toLong(message.get("coverId")))
                // 数组字段：从 vibe-catalog 发送的聚合列表
                .bpmValues(toIntegerList(message.get("bpmValues")))
                .musicalKeys(toStringList(message.get("musicalKeys")))
                .durations(toIntegerList(message.get("durations")))
                // 从 tags 解析出风格列表（逗号分隔 → 数组）
                .genres(parseTagsToGenres((String) message.get("tags")))
                .tags((String) message.get("tags"))
                .price(message.get("price") != null
                        ? new BigDecimal(message.get("price").toString())
                        : null)
                .status(toInteger(message.get("status")))
                // CLAP 音频特征向量（512 维），用于语义搜索
                .audioVector(toFloatArray(message.get("audioVector")))
                .build();
    }

    // ======================== 标签/风格解析 ========================

    /**
     * 将逗号分隔的 tags 字符串解析为风格列表
     * "ambient, core, electric" → ["ambient", "core", "electric"]
     */
    private List<String> parseTagsToGenres(String tags) {
        if (tags == null || tags.isBlank()) return List.of();
        return Arrays.stream(tags.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .distinct()
                .collect(Collectors.toList());
    }

    // ======================== 类型转换工具 ========================

    /**
     * 安全转换为 Long
     */
    private Long toLong(Object value) {
        if (value == null) return null;
        if (value instanceof Number number) return number.longValue();
        return Long.parseLong(value.toString());
    }

    /**
     * 安全转换为 Integer
     */
    private Integer toInteger(Object value) {
        if (value == null) return null;
        if (value instanceof Number number) return number.intValue();
        return Integer.parseInt(value.toString());
    }

    /**
     * 将消息中的列表转换为 List<Integer>
     * RabbitMQ Jackson 可能反序列化为 List<Integer> 或 List<Number>
     */
    @SuppressWarnings("unchecked")
    private List<Integer> toIntegerList(Object value) {
        if (value == null) return List.of();
        if (value instanceof Collection<?> collection) {
            return collection.stream()
                    .filter(Objects::nonNull)
                    .map(item -> {
                        if (item instanceof Number number) return number.intValue();
                        return Integer.parseInt(item.toString());
                    })
                    .collect(Collectors.toList());
        }
        // 兼容单个值的场景
        return List.of(toInteger(value));
    }

    /**
     * 将消息中的列表转换为 List<String>
     */
    @SuppressWarnings("unchecked")
    private List<String> toStringList(Object value) {
        if (value == null) return List.of();
        if (value instanceof Collection<?> collection) {
            return collection.stream()
                    .filter(Objects::nonNull)
                    .map(Object::toString)
                    .collect(Collectors.toList());
        }
        // 兼容单个值的场景
        return List.of(value.toString());
    }

    /**
     * 将消息中的向量数据转换为 float[]
     * Jackson 反序列化 JSON 数组时可能产生 List<Double> 或 List<Number>
     */
    private float[] toFloatArray(Object value) {
        if (value == null) return null;
        if (value instanceof Collection<?> collection) {
            float[] result = new float[collection.size()];
            int i = 0;
            for (Object item : collection) {
                if (item instanceof Number number) {
                    result[i++] = number.floatValue();
                }
            }
            return i > 0 ? result : null;
        }
        return null;
    }
}
