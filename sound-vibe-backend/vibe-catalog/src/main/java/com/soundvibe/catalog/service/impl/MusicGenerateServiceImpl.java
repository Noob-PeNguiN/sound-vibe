package com.soundvibe.catalog.service.impl;

import com.soundvibe.catalog.model.dto.MusicGenerateDTO;
import com.soundvibe.catalog.model.vo.MusicGenerateVO;
import com.soundvibe.catalog.service.MusicGenerateService;
import com.soundvibe.common.exception.BizException;
import com.soundvibe.common.result.ResultCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * AI 音乐生成服务实现
 * 通过 RestTemplate 调用 vibe-analysis Python 服务的 /api/generate 端点
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MusicGenerateServiceImpl implements MusicGenerateService {

    private final RestTemplate restTemplate;

    @Value("${analysis.service.url}")
    private String analysisServiceUrl;

    @Override
    public MusicGenerateVO generate(MusicGenerateDTO dto) {
        String url = analysisServiceUrl + "/api/generate";

        log.info("调用 AI 音乐生成: prompt='{}', duration={}s", dto.prompt().length() > 60 ? dto.prompt().substring(0, 60) + "..." : dto.prompt(), dto.duration());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> requestBody = Map.of(
                "prompt", dto.prompt(),
                "duration", dto.duration()
        );

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                    url, HttpMethod.POST, entity, Map.class
            );

            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                log.error("AI 生成服务返回异常: status={}", response.getStatusCode());
                throw new BizException(ResultCode.SYSTEM_ERROR, "AI 音乐生成失败，请稍后重试");
            }

            String audioUrl = (String) response.getBody().get("url");
            if (audioUrl == null) {
                throw new BizException(ResultCode.SYSTEM_ERROR, "AI 生成服务返回数据异常");
            }

            log.info("AI 音乐生成成功: url={}", audioUrl.substring(0, Math.min(80, audioUrl.length())));

            return new MusicGenerateVO(audioUrl, dto.prompt(), dto.duration());

        } catch (RestClientException e) {
            log.error("调用 AI 生成服务失败: {}", e.getMessage(), e);
            throw new BizException(ResultCode.SYSTEM_ERROR, "AI 音乐生成服务暂不可用，请稍后重试");
        }
    }
}
