package com.soundvibe.order.model.vo;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 订单项视图对象
 *
 * @author SoundVibe Team
 */
public record OrderItemVO(
        Long id,
        String orderId,
        Long trackId,
        String licenseType,
        BigDecimal price
) implements Serializable {
}
