package com.soundvibe.order.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.soundvibe.common.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 订单项实体
 * 对应数据库表 order_items
 * 每个订单项对应一个被购买的 Track
 *
 * @author SoundVibe Team
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("order_items")
public class OrderItem extends BaseEntity {

    @TableField("order_id")
    private String orderId;

    @TableField("track_id")
    private Long trackId;

    /**
     * 授权类型: LEASE / EXCLUSIVE
     */
    @TableField("license_type")
    private String licenseType;

    @TableField("price")
    private BigDecimal price;
}
