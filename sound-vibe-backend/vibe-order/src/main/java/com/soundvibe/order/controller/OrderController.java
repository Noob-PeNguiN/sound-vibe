package com.soundvibe.order.controller;

import com.soundvibe.common.exception.BizException;
import com.soundvibe.common.result.Result;
import com.soundvibe.common.result.ResultCode;
import com.soundvibe.order.model.vo.OrderVO;
import com.soundvibe.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 订单控制器
 * 提供下单结算、订单查询、支付、取消等接口
 *
 * @author SoundVibe Team
 */
@Slf4j
@RestController
@RequestMapping("/order")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    /**
     * 购物车结算，创建订单
     * POST /order/checkout
     */
    @PostMapping("/checkout")
    public Result<OrderVO> checkout(
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {

        checkUserId(userId);
        log.info("用户下单结算: userId={}", userId);
        var vo = orderService.createOrder(userId);
        return Result.success(vo);
    }

    /**
     * 查询订单详情
     * GET /order/{orderId}
     */
    @GetMapping("/{orderId}")
    public Result<OrderVO> getDetail(
            @PathVariable("orderId") String orderId,
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {

        checkUserId(userId);
        var vo = orderService.getOrderDetail(orderId, userId);
        return Result.success(vo);
    }

    /**
     * 查询用户订单列表
     * GET /order/list
     */
    @GetMapping("/list")
    public Result<List<OrderVO>> getUserOrders(
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {

        checkUserId(userId);
        var list = orderService.getUserOrders(userId);
        return Result.success(list);
    }

    /**
     * 模拟支付（实际项目中由支付回调触发）
     * POST /order/{orderId}/pay
     */
    @PostMapping("/{orderId}/pay")
    public Result<Void> pay(
            @PathVariable("orderId") String orderId,
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {

        checkUserId(userId);
        log.info("模拟支付: orderId={}, userId={}", orderId, userId);
        orderService.payOrder(orderId, userId);
        return Result.success();
    }

    /**
     * 手动取消订单
     * POST /order/{orderId}/cancel
     */
    @PostMapping("/{orderId}/cancel")
    public Result<Void> cancel(
            @PathVariable("orderId") String orderId,
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {

        checkUserId(userId);
        log.info("手动取消订单: orderId={}, userId={}", orderId, userId);
        orderService.cancelOrder(orderId);
        return Result.success();
    }

    private void checkUserId(Long userId) {
        if (userId == null) {
            throw new BizException(ResultCode.UNAUTHORIZED, "缺少用户身份信息（X-User-Id）");
        }
    }
}
