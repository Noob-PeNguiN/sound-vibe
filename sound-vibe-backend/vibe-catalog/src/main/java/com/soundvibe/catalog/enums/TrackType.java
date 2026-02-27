package com.soundvibe.catalog.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 作品类型枚举
 *
 * @author SoundVibe Team
 */
@Getter
@AllArgsConstructor
public enum TrackType {

    /**
     * 单曲（包含 1 个文件：音频或 MIDI）
     */
    SINGLE("SINGLE", "单曲"),

    /**
     * 合集/采样包（包含多个文件，可混合音频 + MIDI）
     */
    PACK("PACK", "合集");

    /**
     * 数据库存储值
     */
    @EnumValue
    @JsonValue
    private final String value;

    /**
     * 类型描述
     */
    private final String description;
}
