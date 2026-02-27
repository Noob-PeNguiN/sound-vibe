package com.soundvibe.catalog.model.vo;

import java.io.Serializable;

/**
 * 作品文件视图对象
 * 包含文件本身信息 + 来自 assets 表的分析元数据（BPM / 调式 / 时长）
 *
 * @author SoundVibe Team
 */
public record TrackFileVO(
        Long id,
        Long assetId,
        String fileType,
        String originalName,
        Integer sortOrder,
        Boolean allowPreview,
        /** 节拍速度（来自 assets 表分析结果） */
        Integer bpm,
        /** 音乐调式（来自 assets 表分析结果，如 "C Major"） */
        String musicalKey,
        /** 音频时长/秒（来自 assets 表分析结果） */
        Integer duration,
        /** 自动标注标签（来自 assets 表 CLAP Zero-Shot 分析结果，逗号分隔） */
        String autoTags
) implements Serializable {
}
