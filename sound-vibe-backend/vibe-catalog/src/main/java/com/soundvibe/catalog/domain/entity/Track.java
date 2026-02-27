package com.soundvibe.catalog.domain.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.soundvibe.catalog.enums.TrackStatus;
import com.soundvibe.catalog.enums.TrackType;
import com.soundvibe.catalog.enums.TrackVisibility;
import com.soundvibe.common.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 作品实体（可交易商品）
 * 对应数据库表 tracks，存储用户发布的可交易内容的元数据
 * <p>
 * 设计说明：
 * - 支持两种作品类型：SINGLE（单曲）和 PACK（合集/采样包）
 * - SINGLE：包含 1 个文件，fileId + fileType 作为便捷冗余字段
 * - PACK：包含多个文件，fileId/fileType 为 null
 * - 所有类型都通过 track_files 中间表管理 track → asset 关联
 * - BPM / 调式 / 时长等分析数据存储在 assets 表，通过 track_files JOIN 获取
 * - genre（风格）为用户手动设置的 track 级别属性，保留在本表
 * <p>
 * 关联说明（逻辑外键，微服务间不建物理 FK）：
 * - producerId → users.id（vibe-auth 模块）
 * - coverId   → assets.id（vibe-asset 模块，IMAGE 类型）
 * - fileId    → assets.id（vibe-asset 模块，AUDIO 或 MIDI 类型，仅 SINGLE 便捷字段）
 * - track_files → assets.id（中间表，管理 track 与 asset 的多对多关系）
 *
 * @author SoundVibe Team
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("tracks")
public class Track extends BaseEntity {

    /**
     * 作品标题
     */
    @TableField("title")
    private String title;

    /**
     * 作品描述
     * 建议用户在此说明 BPM、调式、风格、使用场景等信息
     */
    @TableField("description")
    private String description;

    /**
     * 作品类型: SINGLE=单曲, PACK=合集/采样包
     */
    @TableField("track_type")
    private TrackType trackType;

    /**
     * 发布者 ID（关联 users.id）
     * 不冗余存储 username，查询时通过 UserInfoMapper 获取
     */
    @TableField("producer_id")
    private Long producerId;

    /**
     * 封面资产 ID（关联 assets.id，IMAGE 类型）
     */
    @TableField("cover_id")
    private Long coverId;

    /**
     * 文件资产 ID（关联 assets.id，AUDIO 或 MIDI 类型）
     * 仅 SINGLE 类型使用，PACK 类型此字段为 null
     */
    @TableField("file_id")
    private Long fileId;

    /**
     * 文件类型: AUDIO（音频）, MIDI（MIDI 序列）
     * 仅 SINGLE 类型使用，PACK 类型此字段为 null
     */
    @TableField("file_type")
    private String fileType;

    /**
     * 文件数量（冗余字段，用于列表展示）
     * SINGLE=1, PACK=合集中的实际文件数量
     */
    @TableField("file_count")
    private Integer fileCount;

    /**
     * 价格（null 或 0 表示免费）
     */
    @TableField("price")
    private BigDecimal price;

    /**
     * 作品状态: 0=草稿/私密, 1=已上架/公开
     */
    @TableField("status")
    private TrackStatus status;

    /**
     * 可见范围: 0=仅自己, 1=指定用户, 2=公开
     */
    @TableField("visibility")
    private TrackVisibility visibility;

    /**
     * 标签（逗号分隔, e.g., "trap,dark,140bpm,C minor,one-shot"）
     * 用户自由描述内容类型、风格、BPM、调式等
     */
    @TableField("tags")
    private String tags;

    /**
     * 音乐风格/流派（如 "Trap", "Lo-Fi", "Drill", "R&B"）
     * 用户手动设置的 track 级别属性（非分析数据）
     */
    @TableField("genre")
    private String genre;

    /**
     * 是否允许预览: 0=不允许, 1=允许
     * 付费作品由发布者控制，免费作品默认全曲可听
     */
    @TableField("allow_preview")
    private Boolean allowPreview;

    /**
     * 预览时长（秒），仅付费作品生效，默认 30 秒
     */
    @TableField("preview_duration")
    private Integer previewDuration;

    /**
     * 已售数量（每次购买成功 +1）
     */
    @TableField("sold_count")
    private Integer soldCount;

    /**
     * 库存数量（null 表示不限库存，0 表示售罄）
     */
    @TableField("stock")
    private Integer stock;

    /**
     * 逻辑删除标记: 0=未删除, 1=已删除
     */
    @TableLogic
    @TableField("deleted")
    private Integer deleted;
}
