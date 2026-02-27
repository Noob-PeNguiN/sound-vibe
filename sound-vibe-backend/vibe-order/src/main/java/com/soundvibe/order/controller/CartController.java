package com.soundvibe.order.controller;

import com.soundvibe.common.exception.BizException;
import com.soundvibe.common.result.Result;
import com.soundvibe.common.result.ResultCode;
import com.soundvibe.order.model.dto.CartItemDTO;
import com.soundvibe.order.service.CartService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 购物车控制器
 * 提供购物车的增删查清接口
 *
 * @author SoundVibe Team
 */
@Slf4j
@RestController
@RequestMapping("/order/cart")
@RequiredArgsConstructor
public class CartController {

    private final CartService cartService;

    /**
     * 添加商品到购物车
     * POST /order/cart
     */
    @PostMapping
    public Result<Void> addItem(
            @Validated @RequestBody CartItemDTO item,
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {

        checkUserId(userId);
        log.info("添加购物车: userId={}, trackId={}", userId, item.getTrackId());
        cartService.addItem(userId, item);
        return Result.success();
    }

    /**
     * 移除购物车商品
     * DELETE /order/cart/{trackId}
     */
    @DeleteMapping("/{trackId}")
    public Result<Void> removeItem(
            @PathVariable("trackId") Long trackId,
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {

        checkUserId(userId);
        log.info("移除购物车商品: userId={}, trackId={}", userId, trackId);
        cartService.removeItem(userId, trackId);
        return Result.success();
    }

    /**
     * 获取购物车列表
     * GET /order/cart
     */
    @GetMapping
    public Result<List<CartItemDTO>> getCart(
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {

        checkUserId(userId);
        var items = cartService.getCart(userId);
        return Result.success(items);
    }

    /**
     * 清空购物车
     * DELETE /order/cart
     */
    @DeleteMapping
    public Result<Void> clearCart(
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {

        checkUserId(userId);
        log.info("清空购物车: userId={}", userId);
        cartService.clearCart(userId);
        return Result.success();
    }

    private void checkUserId(Long userId) {
        if (userId == null) {
            throw new BizException(ResultCode.UNAUTHORIZED, "缺少用户身份信息（X-User-Id）");
        }
    }
}
