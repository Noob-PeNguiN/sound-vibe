package com.soundvibe.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.soundvibe.order.domain.entity.Order;
import org.apache.ibatis.annotations.Mapper;

/**
 * 订单 Mapper
 *
 * @author SoundVibe Team
 */
@Mapper
public interface OrderMapper extends BaseMapper<Order> {
}
