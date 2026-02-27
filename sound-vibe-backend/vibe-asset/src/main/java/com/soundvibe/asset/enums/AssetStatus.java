package com.soundvibe.asset.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 资产状态枚举
 * 描述数字资产在生命周期中的状态
 *
 * @author SoundVibe Team
 */
@Getter
@AllArgsConstructor
public enum AssetStatus {

    /**
     * 上传中（文件已到达服务端，正在写入 MinIO）
     */
    UPLOADING(0, "上传中"),

    /**
     * 正常（上传完成，可正常访问）
     */
    NORMAL(1, "正常"),

    /**
     * 已删除（逻辑删除）
     */
    DELETED(2, "已删除"),

    /**
     * 分析中（音频智能分析处理中）
     */
    ANALYZING(3, "分析中"),

    /**
     * 分析失败（音频智能分析处理异常）
     */
    ANALYSIS_FAILED(4, "分析失败");

    /**
     * 数据库存储值
     */
    @EnumValue
    @JsonValue
    private final Integer code;

    /**
     * 状态描述
     */
    private final String description;
}
