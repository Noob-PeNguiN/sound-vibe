package com.soundvibe.catalog.model.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.io.Serializable;

/**
 * 作品文件 DTO（用于 PACK 合集中的文件列表项）
 *
 * @param assetId      文件资产 ID（必填，关联 assets.id）
 * @param fileType     文件类型（AUDIO / MIDI）
 * @param originalName 原始文件名（可选，冗余用于展示）
 * @param sortOrder    排序序号（可选，从 0 开始）
 * @param allowPreview 是否允许预览（可选，默认 true）
 * @author SoundVibe Team
 */
public record TrackFileDTO(
        @NotNull(message = "文件资产 ID 不能为空")
        Long assetId,

        @Size(max = 20, message = "文件类型最长 20 个字符")
        String fileType,

        @Size(max = 255, message = "文件名最长 255 个字符")
        String originalName,

        Integer sortOrder,

        Boolean allowPreview
) implements Serializable {
}
