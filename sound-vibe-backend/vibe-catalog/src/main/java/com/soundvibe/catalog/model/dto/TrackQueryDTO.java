package com.soundvibe.catalog.model.dto;

import java.io.Serializable;

/**
 * Track 查询条件 DTO（不可变 Record）
 * 用于封装列表查询时的筛选参数
 *
 * @param keyword    关键词（按标题、标签、描述模糊搜索）
 * @param tag        标签精确匹配（过滤含指定标签的作品）
 * @param fileType   文件类型过滤（AUDIO / MIDI，仅对 SINGLE 有效）
 * @param trackType  作品类型过滤（SINGLE / PACK）
 * @param status     作品状态（0=私密, 1=公开）
 * @param visibility 可见范围（0=仅自己, 1=指定用户, 2=公开）
 * @param producerId 发布者 ID（用于查询"我的作品"）
 * @param current    当前页码（从 1 开始）
 * @param size       每页大小
 * @author SoundVibe Team
 */
public record TrackQueryDTO(
        String keyword,
        String tag,
        String fileType,
        String trackType,
        Integer status,
        Integer visibility,
        Long producerId,
        long current,
        long size
) implements Serializable {

    /**
     * 默认分页参数的工厂方法
     */
    public TrackQueryDTO {
        if (current <= 0) {
            current = 1;
        }
        if (size <= 0 || size > 100) {
            size = 20;
        }
    }
}
