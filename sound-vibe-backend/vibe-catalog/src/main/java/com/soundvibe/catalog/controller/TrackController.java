package com.soundvibe.catalog.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.soundvibe.catalog.model.dto.TrackPublishDTO;
import com.soundvibe.catalog.model.dto.TrackQueryDTO;
import com.soundvibe.catalog.model.dto.TrackUpdateDTO;
import com.soundvibe.catalog.model.vo.TrackVO;
import com.soundvibe.catalog.service.TrackService;
import com.soundvibe.common.exception.BizException;
import com.soundvibe.common.result.Result;
import com.soundvibe.common.result.ResultCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 作品控制器
 * 提供作品发布、查询、状态切换、可见性管理等接口
 * 支持 SINGLE（单曲）和 PACK（合集/采样包）两种类型
 * 不包含任何业务逻辑，仅负责参数校验和路由分发
 *
 * @author SoundVibe Team
 */
@Slf4j
@RestController
@RequestMapping("/catalog/tracks")
@RequiredArgsConstructor
public class TrackController {

    private final TrackService trackService;

    /**
     * 发布新作品（支持 SINGLE 和 PACK）
     * POST /catalog/tracks
     * 需要登录（X-User-Id 请求头）
     */
    @PostMapping
    public Result<TrackVO> publish(
            @Validated @RequestBody TrackPublishDTO dto,
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {

        checkUserId(userId);
        log.info("发布作品请求: userId={}, title={}, trackType={}, coverId={}, fileId={}, fileType={}, filesCount={}",
                userId, dto.title(), dto.trackType(), dto.coverId(), dto.fileId(), dto.fileType(),
                dto.files() != null ? dto.files().size() : 0);

        var vo = trackService.createTrack(dto, userId);
        return Result.success(vo);
    }

    /**
     * 更新作品信息（部分更新，支持 SINGLE 和 PACK）
     * PUT /catalog/tracks/{id}
     * 需要登录（X-User-Id 请求头），仅作品所有者可操作
     */
    @PutMapping("/{id}")
    public Result<TrackVO> update(
            @PathVariable("id") Long id,
            @Validated @RequestBody TrackUpdateDTO dto,
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {

        checkUserId(userId);
        log.info("更新作品请求: trackId={}, userId={}", id, userId);

        var vo = trackService.updateTrack(id, dto, userId);
        return Result.success(vo);
    }

    /**
     * 切换作品状态（私密 ↔ 公开）
     * PUT /catalog/tracks/{id}/toggle-status
     * 需要登录（X-User-Id 请求头），仅作品所有者可操作
     */
    @PutMapping("/{id}/toggle-status")
    public Result<TrackVO> toggleStatus(
            @PathVariable("id") Long id,
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {

        checkUserId(userId);
        log.info("切换作品状态: trackId={}, userId={}", id, userId);

        var vo = trackService.toggleStatus(id, userId);
        return Result.success(vo);
    }

    /**
     * 设置作品可见范围
     * PUT /catalog/tracks/{id}/visibility
     * Body: { "visibility": 0|1|2 }
     * 需要登录（X-User-Id 请求头），仅作品所有者可操作
     */
    @PutMapping("/{id}/visibility")
    public Result<TrackVO> setVisibility(
            @PathVariable("id") Long id,
            @RequestBody Map<String, Integer> body,
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {

        checkUserId(userId);
        var visibility = body.get("visibility");
        if (visibility == null) {
            throw new BizException(ResultCode.PARAM_ERROR, "缺少 visibility 参数");
        }
        log.info("设置作品可见范围: trackId={}, visibility={}, userId={}", id, visibility, userId);

        var vo = trackService.setVisibility(id, userId, visibility);
        return Result.success(vo);
    }

    /**
     * 删除作品（逻辑删除）
     * DELETE /catalog/tracks/{id}
     * 需要登录（X-User-Id 请求头），仅作品所有者可操作
     */
    @DeleteMapping("/{id}")
    public Result<Void> delete(
            @PathVariable("id") Long id,
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {

        checkUserId(userId);
        log.info("删除作品请求: trackId={}, userId={}", id, userId);

        trackService.deleteTrack(id, userId);
        return Result.success(null);
    }

    /**
     * 删除作品（逻辑删除，兼容不支持 DELETE 的代理链路）
     * POST /catalog/tracks/{id}/delete
     * 需要登录（X-User-Id 请求头），仅作品所有者可操作
     */
    @PostMapping("/{id}/delete")
    public Result<Void> deleteByPost(
            @PathVariable("id") Long id,
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {

        checkUserId(userId);
        log.info("删除作品请求(POST兼容): trackId={}, userId={}", id, userId);

        trackService.deleteTrack(id, userId);
        return Result.success(null);
    }

    /**
     * 获取作品详情（含 PACK 文件列表）
     * GET /catalog/tracks/{id}
     */
    @GetMapping("/{id}")
    public Result<TrackVO> getDetail(@PathVariable("id") Long id) {
        var vo = trackService.getDetail(id);
        return Result.success(vo);
    }

    /**
     * 分页查询作品列表（市场主页）
     * GET /catalog/tracks?keyword=xxx&tag=trap&fileType=AUDIO&trackType=PACK&status=1&visibility=2&producerId=6&current=1&size=20
     */
    @GetMapping
    public Result<IPage<TrackVO>> list(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "tag", required = false) String tag,
            @RequestParam(value = "fileType", required = false) String fileType,
            @RequestParam(value = "trackType", required = false) String trackType,
            @RequestParam(value = "status", required = false) Integer status,
            @RequestParam(value = "visibility", required = false) Integer visibility,
            @RequestParam(value = "producerId", required = false) Long producerId,
            @RequestParam(value = "current", defaultValue = "1") long current,
            @RequestParam(value = "size", defaultValue = "20") long size) {

        var query = new TrackQueryDTO(keyword, tag, fileType, trackType, status, visibility, producerId, current, size);
        log.info("查询作品列表: keyword={}, tag={}, fileType={}, trackType={}, status={}, visibility={}, producerId={}, page={}/{}",
                keyword, tag, fileType, trackType, status, visibility, producerId, current, size);

        var page = trackService.listTracks(query);
        return Result.success(page);
    }

    /**
     * 重建 ES 索引（内部运维接口）
     * POST /catalog/tracks/reindex
     */
    @PostMapping("/reindex")
    public Result<Map<String, Object>> reindex() {
        log.info("触发 ES 全量重建索引");
        int count = trackService.reindexAll();
        return Result.success(Map.of("reindexedCount", count));
    }

    // ==================== Private ====================

    /**
     * 校验用户身份
     */
    private void checkUserId(Long userId) {
        if (userId == null) {
            throw new BizException(ResultCode.UNAUTHORIZED, "缺少用户身份信息（X-User-Id）");
        }
    }
}
