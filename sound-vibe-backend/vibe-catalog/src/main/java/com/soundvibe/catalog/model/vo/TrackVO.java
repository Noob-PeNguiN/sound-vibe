package com.soundvibe.catalog.model.vo;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

/**
 * Track 视图对象（不可变 Record）
 * 用于向客户端返回作品信息，隔离内部实体结构
 * <p>
 * 重构设计（v2）：
 * - files 始终填充（SINGLE=1 条，PACK=N 条），BPM/Key/Duration 从 files 中的 asset 获取
 * - fileId/fileType 保留作为 SINGLE 便捷字段（前端向后兼容）
 * - genre 保留在 track 级别（用户手动设置，非分析数据）
 * - 单独的 bpm/musicalKey/duration 字段已移除，由 files 列表承载
 *
 * @author SoundVibe Team
 */
public record TrackVO(
        Long id,
        String title,
        String description,
        String trackType,
        Long producerId,
        String producerName,
        Long coverId,
        Long fileId,
        String fileType,
        Integer fileCount,
        List<TrackFileVO> files,
        BigDecimal price,
        Integer status,
        Integer visibility,
        String tags,
        /** AI 自动标注标签（从关联音频 assets 聚合，逗号分隔） */
        String autoTags,
        String genre,
        Boolean allowPreview,
        Integer previewDuration,
        Integer soldCount,
        Integer stock,
        Date createTime,
        Date updateTime
) implements Serializable {
}
