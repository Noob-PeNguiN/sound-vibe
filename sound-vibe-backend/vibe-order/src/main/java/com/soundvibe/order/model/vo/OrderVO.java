package com.soundvibe.order.model.vo;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

/**
 * 订单视图对象
 *
 * @author SoundVibe Team
 */
public record OrderVO(
        String id,
        Long userId,
        BigDecimal totalAmount,
        Integer status,
        String statusDesc,
        List<OrderItemVO> items,
        Date createTime,
        Date updateTime
) implements Serializable {
}
