package com.soundvibe.catalog.model.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * Track 更新请求 DTO（不可变 Record）
 * 用于接收客户端编辑作品时的参数，所有字段均可选（仅更新传入的字段）
 * <p>
 * 注意：trackType 不可更改（SINGLE 不能改为 PACK，反之亦然）
 * 对于 PACK 类型，如果传入 files 列表，将全量替换合集中的文件
 *
 * @param title       作品标题（可选）
 * @param description 作品描述（可选）
 * @param fileId      文件资产 ID（可选，仅 SINGLE 类型更换文件）
 * @param fileType    文件类型（可选，仅 SINGLE 类型，AUDIO / MIDI）
 * @param files       文件列表（可选，仅 PACK 类型，全量替换）
 * @param coverId     封面资产 ID（可选，更换封面）
 * @param tags        标签（可选）
 * @param genre       音乐风格（可选）
 * @param price            价格（可选）
 * @param visibility       可见范围（可选）
 * @param allowPreview     是否允许预览（可选）
 * @param previewDuration  预览时长秒数（可选）
 * @param stock            库存数量（可选，null 表示不限库存）
 * @author SoundVibe Team
 */
public record TrackUpdateDTO(
        @Size(max = 200, message = "作品标题最长 200 个字符")
        String title,

        @Size(max = 5000, message = "作品描述最长 5000 个字符")
        String description,

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
