package com.soundvibe.asset.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 资产类型枚举
 * 定义 SoundVibe 平台支持的数字资产类型
 * <p>
 * 设计说明：
 * - AUDIO: 所有音频文件（采样、loop、beat 等），合并了旧版的 AUDIO 和 STEM
 * - IMAGE: 图片文件，仅作为作品封面使用，不作为独立可交易商品
 * - MIDI: MIDI 序列文件，可作为独立可交易商品
 *
 * @author SoundVibe Team
 */
@Getter
@AllArgsConstructor
public enum AssetType {

    /**
     * 音频文件 (mp3, wav, flac, aac, ogg)
     * 包含采样、drum loop、melody loop、beat 等所有音频类型
     */
    AUDIO("AUDIO", Set.of("mp3", "wav", "flac", "aac", "ogg")),

    /**
     * 图片文件 (jpg, jpeg, png, webp)
     * 仅用于作品封面，不作为独立可交易商品
     */
    IMAGE("IMAGE", Set.of("jpg", "jpeg", "png", "webp")),

    /**
     * MIDI 序列文件 (mid, midi)
     * 可作为独立可交易商品
     */
    MIDI("MIDI", Set.of("mid", "midi"));

    /**
     * 数据库存储值
     */
    @EnumValue
    @JsonValue
    private final String value;

    /**
     * 该类型允许的文件扩展名集合
     */
    private final Set<String> allowedExtensions;

    /**
     * 根据文件扩展名推断资产类型
     *
     * @param extension 文件扩展名（不含 '.'）
     * @return 匹配的资产类型
     * @throws IllegalArgumentException 无法匹配时抛出
     */
    public static AssetType fromExtension(String extension) {
        var ext = extension.toLowerCase();
        return Arrays.stream(values())
                .filter(type -> type.getAllowedExtensions().contains(ext))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("不支持的文件扩展名: " + extension));
    }

    /**
     * 获取所有支持的扩展名
     *
     * @return 所有资产类型支持的扩展名集合
     */
    public static Set<String> allSupportedExtensions() {
        return Arrays.stream(values())
                .flatMap(type -> type.getAllowedExtensions().stream())
                .collect(Collectors.toUnmodifiableSet());
    }

    /**
     * 判断此类型是否为可交易商品类型（排除 IMAGE）
     *
     * @return true 如果是可交易类型
     */
    public boolean isTradeable() {
        return this == AUDIO || this == MIDI;
    }
}
