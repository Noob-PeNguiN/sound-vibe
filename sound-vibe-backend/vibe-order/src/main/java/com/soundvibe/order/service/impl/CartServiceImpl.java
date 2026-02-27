package com.soundvibe.order.service.impl;

import com.alibaba.fastjson2.JSON;
import com.soundvibe.order.model.dto.CartItemDTO;
import com.soundvibe.order.service.CartService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 购物车服务实现
 * <p>
 * Redis 存储结构：
 * - Key:     vibe:cart:{userId}
 * - HashKey: {trackId}
 * - Value:   CartItemDTO 的 JSON 字符串
 *
 * @author SoundVibe Team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CartServiceImpl implements CartService {

    private static final String CART_KEY_PREFIX = "vibe:cart:";

    private final RedisTemplate<String, Object> redisTemplate;

    @Override
    public void addItem(Long userId, CartItemDTO item) {
        String key = buildKey(userId);
        String hashKey = String.valueOf(item.getTrackId());
        String json = JSON.toJSONString(item);

        HashOperations<String, String, String> ops = redisTemplate.opsForHash();
        ops.put(key, hashKey, json);
        log.debug("购物车添加商品: userId={}, trackId={}", userId, item.getTrackId());
    }

    @Override
    public void removeItem(Long userId, Long trackId) {
        String key = buildKey(userId);
        String hashKey = String.valueOf(trackId);

        HashOperations<String, String, String> ops = redisTemplate.opsForHash();
        ops.delete(key, hashKey);
        log.debug("购物车移除商品: userId={}, trackId={}", userId, trackId);
    }

    @Override
    public List<CartItemDTO> getCart(Long userId) {
        String key = buildKey(userId);

        HashOperations<String, String, String> ops = redisTemplate.opsForHash();
        Map<String, String> entries = ops.entries(key);

        List<CartItemDTO> items = new ArrayList<>(entries.size());
        for (String json : entries.values()) {
            items.add(JSON.parseObject(json, CartItemDTO.class));
        }
        return items;
    }

    @Override
    public void clearCart(Long userId) {
        String key = buildKey(userId);
        redisTemplate.delete(key);
        log.debug("购物车已清空: userId={}", userId);
    }

    private String buildKey(Long userId) {
        return CART_KEY_PREFIX + userId;
    }
}
