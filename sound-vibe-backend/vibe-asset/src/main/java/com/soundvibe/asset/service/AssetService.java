package com.soundvibe.asset.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.soundvibe.asset.model.dto.AssetDTO;
import org.springframework.web.multipart.MultipartFile;

/**
 * 数字资产服务接口
 * 定义资产上传、查询、删除、重命名等业务逻辑
 *
 * @author SoundVibe Team
 */
public interface AssetService {

    /**
     * 上传文件并保存元数据
     *
     * @param file   上传的文件
     * @param userId 上传者 ID
     * @param type   资产类型（AUDIO / IMAGE / STEM，可选）
     * @return 资产传输对象
     */
    AssetDTO upload(MultipartFile file, Long userId, String type);

    /**
     * 分页查询用户的资产列表
     *
     * @param userId  用户 ID
     * @param type    资产类型过滤（可选）
     * @param keyword 搜索关键词（按文件名模糊匹配，可选）
     * @param current 当前页码（从 1 开始）
     * @param size    每页大小
     * @return 分页结果
     */
    IPage<AssetDTO> listByUser(Long userId, String type, String keyword, long current, long size);

    /**
     * 根据 assetCode 获取单个资产详情
     *
     * @param assetCode 资产编码
     * @param userId    当前用户 ID（用于权限校验）
     * @return 资产传输对象
     */
    AssetDTO getByCode(String assetCode, Long userId);

    /**
     * 逻辑删除资产
     * 同时删除 MinIO 中的文件
     *
     * @param assetCode 资产编码
     * @param userId    当前用户 ID（用于权限校验）
     */
    void delete(String assetCode, Long userId);

    /**
     * 重命名资产（修改原始文件名）
     *
     * @param assetCode 资产编码
     * @param userId    当前用户 ID（用于权限校验）
     * @param newName   新文件名（不含路径，可含扩展名）
     * @return 更新后的资产传输对象
     */
    AssetDTO rename(String assetCode, Long userId, String newName);

    /**
     * 根据数据库主键 ID 获取文件的实时预签名 URL
     * 用于跨微服务场景（如 Catalog 展示封面图）
     * 不校验所有权（已发布作品的封面对所有人可见）
     *
     * @param id 资产数据库主键 ID
     * @return 实时生成的预签名 URL
     */
    String getFileUrl(Long id);

    /**
     * 根据数据库主键 ID 获取文件的下载信息
     * 返回 InputStream + 原始文件名 + 文件大小，用于流式下载
     * 调用方负责关闭 InputStream
     *
     * @param id 资产数据库主键 ID
     * @return 下载信息（包含输入流、文件名、大小）
     */
    DownloadInfo getDownloadInfo(Long id);

    /**
     * 根据数据库主键 ID 获取资产详情（含分析元数据）
     * 用于跨微服务内部调用（如 Catalog 获取 BPM/Key/Duration）
     * 不校验所有权
     *
     * @param id 资产数据库主键 ID
     * @return 资产传输对象
     */
    AssetDTO getById(Long id);

    /**
     * 文件下载信息封装
     */
    record DownloadInfo(java.io.InputStream inputStream, String fileName, long fileSize, String contentType) {}
}
