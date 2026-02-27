package com.soundvibe.order.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.soundvibe.order.enums.OrderStatus;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 订单实体
 * 对应数据库表 orders
 * <p>
 * 主键采用 String 类型 (Snowflake ID / UUID)，作为外部展示的订单号
 *
 * @author SoundVibe Team
 */
@Data
@TableName("orders")
public class Order implements Serializable {

    /**
     * 订单号（雪花算法生成的字符串）
     */
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private String id;

    @TableField("user_id")
    private Long userId;

    @TableField("total_amount")
    private BigDecimal totalAmount;

    @TableField("status")
    private OrderStatus status;

    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private Date createTime;

    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private Date updateTime;

    @TableLogic
    @TableField("deleted")
    private Integer deleted;
}
