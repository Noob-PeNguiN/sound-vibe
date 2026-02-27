package com.soundvibe.catalog.domain.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.soundvibe.common.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 购买记录实体
 * 对应数据库表 purchases
 * <p>
 * 记录用户对作品的购买行为（含免费获取）
 * <p>
 * 关联说明：
 * - userId  → users.id（vibe-auth 模块）
 * - trackId → tracks.id（本模块）
 *
 * @author SoundVibe Team
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("purchases")
public class Purchase extends BaseEntity {

    /**
     * 购买者 ID（关联 users.id）
     */
    @TableField("user_id")
    private Long userId;

    /**
     * 作品 ID（关联 tracks.id）
     */
    @TableField("track_id")
    private Long trackId;

    /**
     * 实付金额（0 = 免费获取）
     */
    @TableField("price_paid")
    private BigDecimal pricePaid;

    /**
     * 逻辑删除标记
     */
    @TableLogic
    @TableField("deleted")
    private Integer deleted;
}
