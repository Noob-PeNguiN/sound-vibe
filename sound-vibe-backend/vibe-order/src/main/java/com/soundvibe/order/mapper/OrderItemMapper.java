package com.soundvibe.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.soundvibe.order.domain.entity.OrderItem;
import org.apache.ibatis.annotations.Mapper;

/**
 * 订单项 Mapper
 *
 * @author SoundVibe Team
 */
@Mapper
public interface OrderItemMapper extends BaseMapper<OrderItem> {
}
