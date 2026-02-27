package com.soundvibe.asset.service.impl;

import cn.hutool.core.io.file.FileNameUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.soundvibe.asset.domain.entity.Asset;
import com.soundvibe.asset.enums.AssetStatus;
import com.soundvibe.asset.enums.AssetType;
import com.soundvibe.asset.infrastructure.AssetEventPublisher;
import com.soundvibe.asset.infrastructure.MinioTemplate;
import com.soundvibe.asset.mapper.AssetMapper;
import com.soundvibe.asset.model.dto.AssetDTO;
import com.soundvibe.asset.service.AssetService;
import com.soundvibe.common.exception.BizException;
import com.soundvibe.common.result.ResultCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;

/**
 * 数字资产服务实现类
 * 处理文件上传、元数据持久化等核心业务逻辑
 *
 * @author SoundVibe Team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AssetServiceImpl implements AssetService {

    private final MinioTemplate minioTemplate;
    private final AssetMapper assetMapper;
    private final AssetEventPublisher assetEventPublisher;

    /**
     * 单文件最大大小限制: 100MB
     */
    private static final long MAX_FILE_SIZE = 100 * 1024 * 1024L;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AssetDTO upload(MultipartFile file, Long userId, String type) {
        // 1. 校验文件
        validateFile(file);

        // 2. 解析文件信息
        var originalName = file.getOriginalFilename();
        var extension = FileNameUtil.extName(originalName);
        if (StrUtil.isBlank(extension)) {
            throw new BizException(ResultCode.FILE_TYPE_NOT_SUPPORTED, "无法识别文件扩展名");
        }

        // 3. 确定资产类型（优先使用显式传入的 type，否则根据扩展名推断）
        var assetType = resolveAssetType(type, extension);

        // 4. 校验扩展名是否在该类型允许范围内
        if (!assetType.getAllowedExtensions().contains(extension.toLowerCase())) {
            throw new BizException(ResultCode.FILE_TYPE_NOT_SUPPORTED,
                    "文件类型 [" + extension + "] 不属于 [" + assetType.getValue() + "] 允许的格式");
        }

        // 5. 生成存储路径: {type}/{year}/{month}/{uuid}.{ext}
        var storageName = generateStorageName(assetType, extension);

        // 6. 上传到 MinIO
        try (var inputStream = file.getInputStream()) {
            minioTemplate.upload(storageName, inputStream, file.getSize(), file.getContentType());
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error("读取上传文件流失败: originalName={}", originalName, e);
            throw new BizException(ResultCode.FILE_UPLOAD_FAILED, "读取上传文件失败");
        }

        // 7. 获取访问 URL
        var url = minioTemplate.getPresignedUrl(storageName);

        // 8. 构建实体 & 持久化
        var asset = new Asset();
        asset.setAssetCode(IdUtil.simpleUUID());
        asset.setUserId(userId);
        asset.setOriginalName(originalName);
        asset.setStorageName(storageName);
        asset.setUrl(url);
        asset.setSize(file.getSize());
        asset.setExtension(extension.toLowerCase());
        asset.setType(assetType);
        asset.setStatus(AssetStatus.NORMAL);

        assetMapper.insert(asset);
        log.info("资产上传成功: id={}, storageName={}, userId={}", asset.getId(), storageName, userId);

        // 9. 发布「资产上传完成」事件到 RabbitMQ（仅音频类型触发分析）
        if (assetType.isTradeable() && assetType == AssetType.AUDIO) {
            assetEventPublisher.publishUploadedEvent(asset.getId(), storageName);
        }

        // 10. 返回 DTO（隔离内部存储细节）
        return toDTO(asset);
    }

    @Override
    public IPage<AssetDTO> listByUser(Long userId, String type, String keyword, long current, long size) {
        var wrapper = new LambdaQueryWrapper<Asset>()
                .eq(Asset::getUserId, userId)
                .eq(Asset::getStatus, AssetStatus.NORMAL)
                .like(StrUtil.isNotBlank(keyword), Asset::getOriginalName, keyword)
                .orderByDesc(Asset::getCreateTime);

        // 可选：按类型过滤
        if (StrUtil.isNotBlank(type)) {
            try {
                var assetType = AssetType.valueOf(type.toUpperCase());
                wrapper.eq(Asset::getType, assetType);
            } catch (IllegalArgumentException e) {
                throw new BizException(ResultCode.PARAM_ERROR, "不支持的资产类型: " + type);
            }
        }

        var page = new Page<Asset>(current, size);
        var result = assetMapper.selectPage(page, wrapper);

        // 转换为 DTO 分页
        return result.convert(this::toDTO);
    }

    @Override
    public AssetDTO getByCode(String assetCode, Long userId) {
        var asset = findByCodeOrThrow(assetCode);
        checkOwnership(asset, userId);
        return toDTO(asset);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(String assetCode, Long userId) {
        var asset = findByCodeOrThrow(assetCode);
        checkOwnership(asset, userId);

        // 1. 逻辑删除数据库记录（MyBatis-Plus @TableLogic 自动处理）
        assetMapper.deleteById(asset.getId());

        // 2. 删除 MinIO 中的文件
        try {
            minioTemplate.remove(asset.getStorageName());
        } catch (Exception e) {
            // MinIO 删除失败不回滚数据库（可后续通过定时任务清理孤立文件）
            log.warn("MinIO 文件删除失败（已标记为逻辑删除）: storageName={}", asset.getStorageName(), e);
        }

        log.info("资产删除成功: assetCode={}, userId={}", assetCode, userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AssetDTO rename(String assetCode, Long userId, String newName) {
        if (StrUtil.isBlank(newName)) {
            throw new BizException(ResultCode.PARAM_ERROR, "新文件名不能为空");
        }
        if (newName.length() > 200) {
            throw new BizException(ResultCode.PARAM_ERROR, "文件名过长（最多 200 个字符）");
        }

        var asset = findByCodeOrThrow(assetCode);
        checkOwnership(asset, userId);

        asset.setOriginalName(newName.trim());
        assetMapper.updateById(asset);

        log.info("资产重命名成功: assetCode={}, newName={}", assetCode, newName);
        return toDTO(asset);
    }

    @Override
    public String getFileUrl(Long id) {
        if (id == null) {
            throw new BizException(ResultCode.PARAM_ERROR, "资产 ID 不能为空");
        }
        var asset = assetMapper.selectById(id);
        if (asset == null) {
            throw new BizException(ResultCode.ASSET_NOT_FOUND, "资产不存在: id=" + id);
        }
        // 根据 storageName 实时生成预签名 URL
        return minioTemplate.getPresignedUrl(asset.getStorageName());
    }

    @Override
    public AssetDTO getById(Long id) {
        if (id == null) {
            throw new BizException(ResultCode.PARAM_ERROR, "资产 ID 不能为空");
        }
        var asset = assetMapper.selectById(id);
        if (asset == null) {
            throw new BizException(ResultCode.ASSET_NOT_FOUND, "资产不存在: id=" + id);
        }
        return toDTO(asset);
    }

    @Override
    public DownloadInfo getDownloadInfo(Long id) {
        if (id == null) {
            throw new BizException(ResultCode.PARAM_ERROR, "资产 ID 不能为空");
        }
        var asset = assetMapper.selectById(id);
        if (asset == null) {
            throw new BizException(ResultCode.ASSET_NOT_FOUND, "资产不存在: id=" + id);
        }

        // 推断 Content-Type
        var contentType = resolveContentType(asset.getExtension());

        // 从 MinIO 获取文件流
        var inputStream = minioTemplate.getObject(asset.getStorageName());
        log.info("准备下载文件: id={}, originalName={}, size={}", id, asset.getOriginalName(), asset.getSize());

        return new DownloadInfo(inputStream, asset.getOriginalName(), asset.getSize(), contentType);
    }

    // ======================== Private Methods ========================

    /**
     * 根据 assetCode 查找资产，不存在则抛异常
     */
    private Asset findByCodeOrThrow(String assetCode) {
        var wrapper = new LambdaQueryWrapper<Asset>()
                .eq(Asset::getAssetCode, assetCode);
        var asset = assetMapper.selectOne(wrapper);
        if (asset == null) {
            throw new BizException(ResultCode.ASSET_NOT_FOUND, "资产不存在: " + assetCode);
        }
        return asset;
    }

    /**
     * 校验当前用户是否为资产的所有者
     */
    private void checkOwnership(Asset asset, Long userId) {
        if (!asset.getUserId().equals(userId)) {
            throw new BizException(ResultCode.FORBIDDEN, "无权操作此资产");
        }
    }

    /**
     * 校验上传文件的合法性
     */
    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BizException(ResultCode.FILE_IS_EMPTY);
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BizException(ResultCode.FILE_SIZE_EXCEEDED,
                    "文件大小 [" + file.getSize() + " bytes] 超过限制 [100MB]");
        }
    }

    /**
     * 解析资产类型
     * 优先使用显式传入的 type 参数，否则根据文件扩展名自动推断
     */
    private AssetType resolveAssetType(String type, String extension) {
        if (StrUtil.isNotBlank(type)) {
            try {
                return AssetType.valueOf(type.toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new BizException(ResultCode.PARAM_ERROR, "不支持的资产类型: " + type);
            }
        }
        return AssetType.fromExtension(extension);
    }

    /**
     * 生成 MinIO 对象存储名称
     * 格式: {type}/{year}/{month}/{uuid}.{ext}
     * 示例: audio/2026/02/a1b2c3d4e5f6.mp3
     */
    private String generateStorageName(AssetType assetType, String extension) {
        var now = LocalDate.now();
        var year = String.valueOf(now.getYear());
        var month = String.format("%02d", now.getMonthValue());
        var uuid = IdUtil.simpleUUID();
        return String.join("/",
                assetType.getValue().toLowerCase(),
                year,
                month,
                uuid + "." + extension.toLowerCase()
        );
    }

    /**
     * 根据文件扩展名推断 Content-Type
     */
    private String resolveContentType(String extension) {
        if (extension == null) return "application/octet-stream";
        return switch (extension.toLowerCase()) {
            case "mp3" -> "audio/mpeg";
            case "wav" -> "audio/wav";
            case "flac" -> "audio/flac";
            case "aac" -> "audio/aac";
            case "ogg" -> "audio/ogg";
            case "mid", "midi" -> "audio/midi";
            case "jpg", "jpeg" -> "image/jpeg";
            case "png" -> "image/png";
            case "webp" -> "image/webp";
            default -> "application/octet-stream";
        };
    }

    /**
     * 实体 -> DTO 转换
     * 只暴露客户端所需字段，避免泄漏内部存储路径和数据库主键
     */
    private AssetDTO toDTO(Asset asset) {
        return new AssetDTO(
                asset.getId(),
                asset.getAssetCode(),
                asset.getOriginalName(),
                asset.getUrl(),
                asset.getSize(),
                asset.getExtension(),
                asset.getType().getValue(),
                asset.getStatus().getCode(),
                asset.getBpm(),
                asset.getMusicalKey(),
                asset.getDuration(),
                asset.getAutoTags(),
                asset.getCreateTime()
        );
    }
}
