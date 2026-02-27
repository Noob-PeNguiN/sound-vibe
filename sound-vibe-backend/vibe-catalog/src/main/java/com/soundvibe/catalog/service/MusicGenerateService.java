package com.soundvibe.catalog.service;

import com.soundvibe.catalog.model.dto.MusicGenerateDTO;
import com.soundvibe.catalog.model.vo.MusicGenerateVO;

/**
 * AI 音乐生成服务接口
 */
public interface MusicGenerateService {

    /**
     * 调用 vibe-analysis 的 MusicGen 模型生成音频
     *
     * @param dto 生成请求参数
     * @return 生成结果（含下载 URL）
     */
    MusicGenerateVO generate(MusicGenerateDTO dto);
}
