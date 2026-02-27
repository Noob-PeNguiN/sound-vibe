package com.soundvibe.order.service;

import com.soundvibe.order.model.dto.CartItemDTO;

import java.util.List;

/**
 * 购物车服务接口
 * 基于 Redis Hash 实现高性能购物车
 *
 * @author SoundVibe Team
 */
public interface CartService {

    /**
     * 添加商品到购物车
     */
    void addItem(Long userId, CartItemDTO item);

    /**
     * 从购物车移除商品
     */
    void removeItem(Long userId, Long trackId);

    /**
     * 获取用户购物车所有商品
     */
    List<CartItemDTO> getCart(Long userId);

    /**
     * 清空用户购物车
     */
    void clearCart(Long userId);
}
