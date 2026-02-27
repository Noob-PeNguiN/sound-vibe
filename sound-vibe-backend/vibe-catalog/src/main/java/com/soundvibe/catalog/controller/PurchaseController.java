package com.soundvibe.catalog.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.soundvibe.catalog.model.vo.PurchaseVO;
import com.soundvibe.catalog.service.PurchaseService;
import com.soundvibe.common.exception.BizException;
import com.soundvibe.common.result.Result;
import com.soundvibe.common.result.ResultCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 购买控制器
 * 提供作品购买、已购列表查询、购买状态检查等接口
 * <p>
 * 当前阶段为模拟购买，不涉及真实支付
 *
 * @author SoundVibe Team
 */
@Slf4j
@RestController
@RequestMapping("/catalog/purchases")
@RequiredArgsConstructor
public class PurchaseController {

    private final PurchaseService purchaseService;

    /**
     * 购买作品（模拟支付，直接成功）
     * POST /catalog/purchases/{trackId}
     * 需要登录（X-User-Id 请求头）
     */
    @PostMapping("/{trackId}")
    public Result<PurchaseVO> purchase(
            @PathVariable("trackId") Long trackId,
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {

        checkUserId(userId);
        log.info("购买作品请求: trackId={}, userId={}", trackId, userId);

        var vo = purchaseService.purchaseTrack(trackId, userId);
        return Result.success(vo);
    }

    /**
     * 查询我的已购作品列表
     * GET /catalog/purchases?current=1&size=20
     * 需要登录（X-User-Id 请求头）
     */
    @GetMapping
    public Result<IPage<PurchaseVO>> listMyPurchases(
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @RequestParam(value = "current", defaultValue = "1") long current,
            @RequestParam(value = "size", defaultValue = "20") long size) {

        checkUserId(userId);
        log.info("查询已购列表: userId={}, page={}/{}", userId, current, size);

        var page = purchaseService.listMyPurchases(userId, current, size);
        return Result.success(page);
    }

    /**
     * 检查当前用户是否已购买某作品
     * GET /catalog/purchases/check/{trackId}
     * 需要登录（X-User-Id 请求头）
     */
    @GetMapping("/check/{trackId}")
    public Result<Boolean> checkPurchased(
            @PathVariable("trackId") Long trackId,
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {

        checkUserId(userId);
        var purchased = purchaseService.hasPurchased(trackId, userId);
        return Result.success(purchased);
    }

    /**
     * 内部接口：订单支付成功后同步购买记录
     * POST /catalog/purchases/confirm
     * 由 vibe-order 通过 Feign 调用，不经过网关
     */
    @PostMapping("/confirm")
    public Result<Void> confirmPurchase(
            @RequestParam("trackId") Long trackId,
            @RequestParam("userId") Long userId,
            @RequestParam("pricePaid") java.math.BigDecimal pricePaid) {

        log.info("内部接口 - 确认购买: trackId={}, userId={}, pricePaid={}", trackId, userId, pricePaid);
        purchaseService.confirmPurchase(trackId, userId, pricePaid);
        return Result.success();
    }

    // ==================== Private ====================

    private void checkUserId(Long userId) {
        if (userId == null) {
            throw new BizException(ResultCode.UNAUTHORIZED, "缺少用户身份信息（X-User-Id）");
        }
    }
}
