package com.soundvibe.order.service;

import com.soundvibe.order.model.vo.OrderVO;

import java.util.List;

/**
 * 订单服务接口
 *
 * @author SoundVibe Team
 */
public interface OrderService {

    /**
     * 从购物车结算创建订单
     * 包含：分布式锁防超卖、Feign 价格校验、延迟队列超时取消
     */
    OrderVO createOrder(Long userId);

    /**
     * 查询订单详情
     */
    OrderVO getOrderDetail(String orderId, Long userId);

    /**
     * 查询用户订单列表
     */
    List<OrderVO> getUserOrders(Long userId);

    /**
     * 取消订单（超时自动调用或用户手动取消）
     */
    void cancelOrder(String orderId);

    /**
     * 模拟支付成功回调
     */
    void payOrder(String orderId, Long userId);
}
