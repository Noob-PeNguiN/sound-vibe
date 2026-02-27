package com.soundvibe.asset.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.soundvibe.asset.model.dto.AssetDTO;
import com.soundvibe.asset.model.dto.RenameRequest;
import com.soundvibe.asset.service.AssetService;
import com.soundvibe.common.exception.BizException;
import com.soundvibe.common.result.Result;
import com.soundvibe.common.result.ResultCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * 数字资产控制器
 * 提供文件上传、查询、删除、重命名等接口
 * 不包含任何业务逻辑，仅负责参数校验和路由分发
 *
 * @author SoundVibe Team
 */
@Slf4j
@RestController
@RequestMapping("/assets")
@RequiredArgsConstructor
public class AssetController {

    private final AssetService assetService;

    /**
     * 文件上传
     * POST /assets/upload
     */
    @PostMapping("/upload")
    public Result<AssetDTO> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "type", required = false) String type,
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {

        checkUserId(userId);
        log.info("收到文件上传请求: userId={}, fileName={}, size={}, type={}",
                userId, file.getOriginalFilename(), file.getSize(), type);

        var assetDTO = assetService.upload(file, userId, type);
        return Result.success(assetDTO);
    }

    /**
     * 查询当前用户的资产列表（分页）
     * GET /assets?current=1&size=20&type=AUDIO&keyword=xxx
     */
    @GetMapping
    public Result<IPage<AssetDTO>> list(
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @RequestParam(value = "type", required = false) String type,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "current", defaultValue = "1") long current,
            @RequestParam(value = "size", defaultValue = "20") long size) {

        checkUserId(userId);
        log.info("查询资产列表: userId={}, type={}, keyword={}, page={}/{}",
                userId, type, keyword, current, size);

        var page = assetService.listByUser(userId, type, keyword, current, size);
        return Result.success(page);
    }

    /**
     * 获取单个资产详情
     * GET /assets/{assetCode}
     */
    @GetMapping("/{assetCode}")
    public Result<AssetDTO> getByCode(
            @PathVariable("assetCode") String assetCode,
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {

        checkUserId(userId);
        var assetDTO = assetService.getByCode(assetCode, userId);
        return Result.success(assetDTO);
    }

    /**
     * 删除资产（逻辑删除 + MinIO 物理删除）
     * DELETE /assets/{assetCode}
     */
    @DeleteMapping("/{assetCode}")
    public Result<Void> delete(
            @PathVariable("assetCode") String assetCode,
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {

        checkUserId(userId);
        log.info("删除资产请求: userId={}, assetCode={}", userId, assetCode);

        assetService.delete(assetCode, userId);
        return Result.success(null);
    }

    /**
     * 根据资产 ID 获取资产详情（含分析元数据）
     * GET /assets/{id}/detail
     * <p>
     * 用途：跨微服务内部调用（如 vibe-catalog 获取 BPM/Key/Duration）
     * 不需要鉴权，仅供服务间调用
     */
    @GetMapping("/{id}/detail")
    public Result<AssetDTO> getById(@PathVariable("id") Long id) {
        log.info("内部查询资产详情: id={}", id);
        var assetDTO = assetService.getById(id);
        return Result.success(assetDTO);
    }

    /**
     * 根据资产 ID 获取文件（302 重定向到实时预签名 URL）
     * GET /assets/file/{id}
     * <p>
     * 用途：跨微服务展示资产文件（如 Catalog 页面展示封面图）
     * 不需要鉴权，已发布作品的封面/音频对所有人可见
     */
    @GetMapping("/file/{id}")
    public ResponseEntity<Void> getFile(@PathVariable("id") Long id) {
        log.info("请求资产文件: id={}", id);
        var url = assetService.getFileUrl(id);
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(url))
                .build();
    }

    /**
     * 下载资产文件（流式返回文件内容）
     * GET /assets/download/{id}
     * <p>
     * 与 /assets/file/{id}（302 重定向）不同，此端点直接返回文件字节流，
     * 并设置 Content-Disposition: attachment 强制浏览器下载。
     * 解决前端跨域 302 重定向导致无法触发下载的问题。
     */
    @GetMapping("/download/{id}")
    public ResponseEntity<InputStreamResource> download(@PathVariable("id") Long id) {
        log.info("下载资产文件: id={}", id);
        var downloadInfo = assetService.getDownloadInfo(id);

        // 中文文件名需要 URL 编码，遵循 RFC 5987
        var encodedFileName = URLEncoder.encode(downloadInfo.fileName(), StandardCharsets.UTF_8)
                .replace("+", "%20");

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + encodedFileName + "\"; filename*=UTF-8''" + encodedFileName)
                .header(HttpHeaders.CONTENT_TYPE, downloadInfo.contentType())
                .header(HttpHeaders.CONTENT_LENGTH, String.valueOf(downloadInfo.fileSize()))
                .body(new InputStreamResource(downloadInfo.inputStream()));
    }

    /**
     * 重命名资产
     * PUT /assets/{assetCode}/rename
     */
    @PutMapping("/{assetCode}/rename")
    public Result<AssetDTO> rename(
            @PathVariable("assetCode") String assetCode,
            @RequestBody RenameRequest request,
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {

        checkUserId(userId);
        log.info("重命名资产请求: userId={}, assetCode={}, newName={}",
                userId, assetCode, request.newName());

        var assetDTO = assetService.rename(assetCode, userId, request.newName());
        return Result.success(assetDTO);
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
