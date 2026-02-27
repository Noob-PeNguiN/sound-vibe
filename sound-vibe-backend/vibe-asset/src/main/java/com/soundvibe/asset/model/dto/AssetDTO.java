package com.soundvibe.asset.model.dto;

import java.io.Serializable;
import java.util.Date;

/**
 * 资产数据传输对象（不可变 Record）
 * 用于向客户端返回资产信息，隐藏内部存储细节
 * <p>
 * 安全设计：
 * - 使用 assetCode（UUID）替代数据库自增主键 id，防止用户推测平台数据量
 * - 不暴露 storageName（MinIO 内部路径）
 * <p>
 * 注意：id 字段用于跨微服务关联（如 Catalog 模块的 tracks.cover_id / tracks.audio_id）
 * 对外展示仍优先使用 assetCode
 *
 * @param id           数据库主键（用于跨服务关联）
 * @param assetCode    资产编码（对外唯一标识）
 * @param originalName 原始文件名
 * @param url          文件访问 URL
 * @param size         文件大小（字节）
 * @param extension    文件扩展名
 * @param type         资产类型
 * @param status       资产状态
 * @param autoTags     自动标注标签（逗号分隔）
 * @param createTime   创建时间
 * @author SoundVibe Team
 */
public record AssetDTO(
        Long id,
        String assetCode,
        String originalName,
        String url,
        Long size,
        String extension,
        String type,
        Integer status,
        Integer bpm,
        String musicalKey,
        Integer duration,
        String autoTags,
        Date createTime
) implements Serializable {
}
