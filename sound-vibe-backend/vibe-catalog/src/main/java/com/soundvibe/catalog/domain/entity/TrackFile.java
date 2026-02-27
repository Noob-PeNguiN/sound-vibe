package com.soundvibe.catalog.domain.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.soundvibe.common.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 作品文件实体（PACK 合集中的文件列表项）
 * 对应数据库表 track_files
 * <p>
 * 设计说明：
 * - 用于 PACK 类型的作品，记录合集中包含的每个文件
 * - SINGLE 类型的作品不使用此表（直接用 tracks.file_id / file_type）
 * - sort_order 用于控制文件在合集中的展示顺序
 * <p>
 * 关联说明：
 * - trackId → tracks.id（本模块内）
 * - assetId → assets.id（vibe-asset 模块，AUDIO 或 MIDI 类型）
 *
 * @author SoundVibe Team
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("track_files")
public class TrackFile extends BaseEntity {

    /**
     * 所属作品 ID（关联 tracks.id）
     */
    @TableField("track_id")
    private Long trackId;

    /**
     * 文件资产 ID（关联 assets.id）
     */
    @TableField("asset_id")
    private Long assetId;

    /**
     * 文件类型: AUDIO / MIDI
     */
    @TableField("file_type")
    private String fileType;

    /**
     * 原始文件名（冗余存储，避免跨服务查询）
     */
    @TableField("original_name")
    private String originalName;

    /**
     * 排序序号（从 0 开始，越小越靠前）
     */
    @TableField("sort_order")
    private Integer sortOrder;

    /**
     * 是否允许预览: false=不允许, true=允许
     * 发布者可控制合集中哪些文件可以被试听
     */
    @TableField("allow_preview")
    private Boolean allowPreview;

    /**
     * 逻辑删除标记
     */
    @TableLogic
    @TableField("deleted")
    private Integer deleted;
}
