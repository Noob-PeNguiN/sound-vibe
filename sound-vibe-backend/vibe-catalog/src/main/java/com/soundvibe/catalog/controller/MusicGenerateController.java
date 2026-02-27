package com.soundvibe.catalog.controller;

import com.soundvibe.catalog.model.dto.MusicGenerateDTO;
import com.soundvibe.catalog.model.vo.MusicGenerateVO;
import com.soundvibe.catalog.service.MusicGenerateService;
import com.soundvibe.common.exception.BizException;
import com.soundvibe.common.result.Result;
import com.soundvibe.common.result.ResultCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * AI 音乐生成控制器
 * 通过 MusicGen 模型根据文本描述生成音频片段
 *
 * @author SoundVibe Team
 */
@Slf4j
@RestController
@RequestMapping("/catalog/ai")
@RequiredArgsConstructor
public class MusicGenerateController {

    private final MusicGenerateService musicGenerateService;

    /**
     * 文本生成音乐
     * POST /catalog/ai/generate
     * 需要登录（X-User-Id 请求头）
     */
    @PostMapping("/generate")
    public Result<MusicGenerateVO> generate(
            @Validated @RequestBody MusicGenerateDTO dto,
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {

        if (userId == null) {
            throw new BizException(ResultCode.UNAUTHORIZED, "缺少用户身份信息（X-User-Id）");
        }

        log.info("AI 音乐生成请求: userId={}, prompt='{}', duration={}s",
                userId,
                dto.prompt().length() > 40 ? dto.prompt().substring(0, 40) + "..." : dto.prompt(),
                dto.duration());

        var vo = musicGenerateService.generate(dto);
        return Result.success(vo);
    }
}
