package com.soundvibe.order.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 订单状态枚举
 *
 * @author SoundVibe Team
 */
@Getter
@AllArgsConstructor
public enum OrderStatus {

    PENDING(0, "待支付"),
    PAID(1, "已支付"),
    CANCELLED(2, "已取消");

    @EnumValue
    private final int code;

    private final String description;
}
