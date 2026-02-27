package com.soundvibe.catalog.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 音乐作品状态枚举
 *
 * @author SoundVibe Team
 */
@Getter
@AllArgsConstructor
public enum TrackStatus {

    /**
     * 私密（仅自己可见）
     */
    DRAFT(0, "私密"),

    /**
     * 公开（已发布）
     */
    PUBLISHED(1, "公开");

    /**
     * 数据库存储值
     */
    @EnumValue
    private final int code;

    /**
     * 状态描述
     */
    private final String description;
}
