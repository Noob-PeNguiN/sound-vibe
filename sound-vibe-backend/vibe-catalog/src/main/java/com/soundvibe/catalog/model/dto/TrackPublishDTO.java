package com.soundvibe.catalog.model.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * Track 发布请求 DTO（不可变 Record）
 * 用于接收客户端发布新作品时的参数
 * <p>
 * 支持两种模式：
 * - SINGLE（单曲）：传 fileId + fileType，files 为 null 或空
 * - PACK（合集）：传 files 列表，fileId / fileType 为 null
 * <p>
 * 通过 Jakarta Validation 注解在 Controller 层校验
 *
 * @param title       作品标题（必填）
 * @param description 作品描述（可选，建议填写 BPM、调式、风格等信息）
 * @param trackType   作品类型（可选，SINGLE / PACK，默认 SINGLE）
 * @param fileId      文件资产 ID（SINGLE 时使用，关联 assets.id）
 * @param fileType    文件类型（SINGLE 时使用，AUDIO / MIDI，默认 AUDIO）
 * @param files       文件列表（PACK 时使用，包含多个文件信息）
 * @param coverId     封面资产 ID（可选，关联 assets.id，IMAGE 类型）
 * @param tags        标签（可选，逗号分隔）
 * @param genre       音乐风格（可选，如 "Trap", "Lo-Fi"）
 * @param price            价格（可选，null 或 0 表示免费）
 * @param visibility       可见范围（可选，0=私有 1=指定用户 2=公开，默认公开）
 * @param allowPreview     是否允许预览（可选，默认 true，仅付费作品有意义）
 * @param previewDuration  预览时长秒数（可选，默认 30，仅付费作品生效）
 * @param stock            库存数量（可选，null 表示不限库存）
 * @author SoundVibe Team
 */
public record TrackPublishDTO(
        @NotBlank(message = "作品标题不能为空")
        @Size(max = 200, message = "作品标题最长 200 个字符")
        String title,

        @Size(max = 5000, message = "作品描述最长 5000 个字符")
        String description,

        @Size(max = 10, message = "作品类型最长 10 个字符")
        String trackType,

        Long fileId,

        @Size(max = 20, message = "文件类型最长 20 个字符")
        String fileType,

        @Valid
        @Size(max = 200, message = "合集最多包含 200 个文件")
        List<TrackFileDTO> files,

        Long coverId,

        @Size(max = 500, message = "标签最长 500 个字符")
        String tags,

        @Size(max = 50, message = "音乐风格最长 50 个字符")
        String genre,

        BigDecimal price,

        Integer visibility,

        Boolean allowPreview,

        Integer previewDuration,

        Integer stock
) implements Serializable {
}
