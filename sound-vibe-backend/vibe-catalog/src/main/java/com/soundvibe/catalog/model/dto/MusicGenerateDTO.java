package com.soundvibe.catalog.model.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.io.Serializable;

/**
 * AI 音乐生成请求 DTO
 *
 * @param prompt   音乐描述文本（如 "A heavy electronic drum loop with dark bassline"）
 * @param duration 生成时长（秒），范围 1~30，默认 5
 */
public record MusicGenerateDTO(
        @NotBlank(message = "音乐描述不能为空")
        @Size(max = 512, message = "描述文本不能超过 512 个字符")
        String prompt,

        @Min(value = 1, message = "时长不能小于 1 秒")
        @Max(value = 30, message = "时长不能超过 30 秒")
        Integer duration
) implements Serializable {

    public MusicGenerateDTO {
        if (duration == null) {
            duration = 5;
        }
    }
}
