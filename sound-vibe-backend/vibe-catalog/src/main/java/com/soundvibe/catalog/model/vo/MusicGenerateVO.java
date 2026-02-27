package com.soundvibe.catalog.model.vo;

import java.io.Serializable;

/**
 * AI 音乐生成响应 VO
 *
 * @param url      生成音频的下载 URL（MinIO 预签名，24 小时有效）
 * @param prompt   原始描述文本
 * @param duration 生成时长（秒）
 */
public record MusicGenerateVO(
        String url,
        String prompt,
        Integer duration
) implements Serializable {
}
