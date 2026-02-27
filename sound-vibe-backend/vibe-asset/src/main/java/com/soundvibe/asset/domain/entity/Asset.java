package com.soundvibe.asset.domain.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.soundvibe.asset.enums.AssetStatus;
import com.soundvibe.asset.enums.AssetType;
import com.soundvibe.common.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 数字资产实体
 * 对应数据库表 assets，存储文件元数据信息
 *
 * @author SoundVibe Team
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("assets")
public class Asset extends BaseEntity {

    /**
     * 资产编码（对外唯一标识，替代主键 ID 暴露给前端）
     * 使用 UUID（32位十六进制），不可推测平台数据量
     */
    @TableField("asset_code")
    private String assetCode;

    /**
     * 上传者用户 ID（关联 users.id）
     */
    @TableField("user_id")
    private Long userId;

    /**
     * 原始文件名（用户上传时的文件名）
     */
    @TableField("original_name")
    private String originalName;

    /**
     * MinIO 存储对象名（含路径前缀）
     * 格式: {type}/{year}/{month}/{uuid}.{ext}
     */
    @TableField("storage_name")
    private String storageName;

    /**
     * 文件访问 URL（预签名或公开链接）
     */
    @TableField("url")
    private String url;

    /**
     * 文件大小（字节）
     */
    @TableField("size")
    private Long size;

    /**
     * 文件扩展名（不含 '.'）
     */
    @TableField("extension")
    private String extension;

    /**
     * 资产类型: AUDIO, IMAGE, STEM
     */
    @TableField("type")
    private AssetType type;

    /**
     * 资产状态: 0=上传中, 1=正常, 2=已删除, 3=分析中, 4=分析失败
     */
    @TableField("status")
    private AssetStatus status;

    /**
     * 节拍速度 (Beats Per Minute)
     * 由 vibe-analysis 服务异步分析填充，仅音频类型有效
     */
    @TableField("bpm")
    private Integer bpm;

    /**
     * 音乐调性（如 C Major, A Minor）
     * 由 vibe-analysis 服务异步分析填充，仅音频类型有效
     */
    @TableField("musical_key")
    private String musicalKey;

    /**
     * 音频时长（秒）
     * 由 vibe-analysis 服务异步分析填充，仅音频类型有效
     */
    @TableField("duration")
    private Integer duration;

    /**
     * 自动标注标签（逗号分隔）
     * 由 vibe-analysis 服务通过 CLAP Zero-Shot Audio Tagging 生成
     */
    @TableField("auto_tags")
    private String autoTags;

    /**
     * 逻辑删除标记: 0=未删除, 1=已删除
     * MyBatis-Plus 会自动在查询时追加 WHERE deleted = 0
     */
    @TableLogic
    @TableField("deleted")
    private Integer deleted;
}
