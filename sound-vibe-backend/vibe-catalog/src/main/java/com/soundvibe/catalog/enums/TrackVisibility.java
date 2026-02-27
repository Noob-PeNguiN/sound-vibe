package com.soundvibe.catalog.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 作品可见范围枚举
 * 控制作品对其他用户的可见性
 *
 * @author SoundVibe Team
 */
@Getter
@AllArgsConstructor
public enum TrackVisibility {

    /**
     * 仅自己可见（私有）
     */
    PRIVATE(0, "仅自己可见"),

    /**
     * 指定用户可见（协作者，预留）
     */
    SHARED(1, "指定用户可见"),

    /**
     * 公开可见
     */
    PUBLIC(2, "公开");

    /**
     * 数据库存储值
     */
    @EnumValue
    private final int code;

    /**
     * 可见性描述
     */
    private final String description;
}
