package com.soundvibe.order.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.soundvibe.common.exception.BizException;
import com.soundvibe.common.result.Result;
import com.soundvibe.common.result.ResultCode;
import com.soundvibe.order.config.RabbitMQConfig;
import com.soundvibe.order.domain.entity.Order;
import com.soundvibe.order.domain.entity.OrderItem;
import com.soundvibe.order.enums.OrderStatus;
import com.soundvibe.order.feign.CatalogFeignClient;
import com.soundvibe.order.mapper.OrderItemMapper;
import com.soundvibe.order.mapper.OrderMapper;
import com.soundvibe.order.model.dto.CartItemDTO;
import com.soundvibe.order.model.vo.OrderItemVO;
import com.soundvibe.order.model.vo.OrderVO;
import com.soundvibe.order.service.CartService;
import com.soundvibe.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * 订单服务实现
 * <p>
 * 核心流程：
 * 1. 读取购物车 → 2. 独占锁防超卖 → 3. Feign 校验价格/库存
 * → 4. 事务写 DB → 5. 清购物车 → 6. 发送延迟取消消息
 *
 * @author SoundVibe Team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private static final String LOCK_KEY_PREFIX = "vibe:lock:track:";

    private final CartService cartService;
    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;
    private final RedissonClient redissonClient;
    private final CatalogFeignClient catalogFeignClient;
    private final RabbitTemplate rabbitTemplate;

    @Value("${order.timeout.ttl-ms:900000}")
    private long orderTimeoutMs;

    @Override
    public OrderVO createOrder(Long userId) {
        // 1. 获取购物车
        List<CartItemDTO> cartItems = cartService.getCart(userId);
        if (cartItems == null || cartItems.isEmpty()) {
            throw new BizException("购物车为空，无法创建订单");
        }

        List<RLock> acquiredLocks = new ArrayList<>();
        try {
            // 2. 对 EXCLUSIVE 类型的商品加分布式锁
            for (CartItemDTO item : cartItems) {
                if ("EXCLUSIVE".equalsIgnoreCase(item.getLicenseType())) {
                    RLock lock = redissonClient.getLock(LOCK_KEY_PREFIX + item.getTrackId());
                    boolean locked = lock.tryLock(3, 30, TimeUnit.SECONDS);
                    if (!locked) {
                        throw new BizException("作品 [" + item.getTitle() + "] 正在被其他用户购买，请稍后再试");
                    }
                    acquiredLocks.add(lock);
                }
            }

            // 3. Feign 校验价格和库存
            validateTracksAvailability(cartItems);

            // 4. 创建订单（事务）
            Order order = saveOrderAndItems(userId, cartItems);

            // 5. 清空购物车
            cartService.clearCart(userId);

            // 6. 发送延迟取消消息
            sendDelayedCancelMessage(order.getId());

            log.info("订单创建成功: orderId={}, userId={}, totalAmount={}",
                    order.getId(), userId, order.getTotalAmount());

            return buildOrderVO(order, cartItems);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BizException("下单过程中断，请重试");
        } finally {
            // 确保释放所有已获取的锁
            for (RLock lock : acquiredLocks) {
                if (lock.isHeldByCurrentThread()) {
                    lock.unlock();
                }
            }
        }
    }

    @Override
    public OrderVO getOrderDetail(String orderId, Long userId) {
        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BizException(ResultCode.NOT_FOUND, "订单不存在");
        }
        if (!order.getUserId().equals(userId)) {
            throw new BizException(ResultCode.FORBIDDEN, "无权查看此订单");
        }
        return buildOrderVOFromDb(order);
    }

    @Override
    public List<OrderVO> getUserOrders(Long userId) {
        var wrapper = new LambdaQueryWrapper<Order>()
                .eq(Order::getUserId, userId)
                .orderByDesc(Order::getCreateTime);
        List<Order> orders = orderMapper.selectList(wrapper);

        return orders.stream().map(this::buildOrderVOFromDb).toList();
    }

    @Override
    public void cancelOrder(String orderId) {
        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            log.warn("取消订单失败，订单不存在: orderId={}", orderId);
            return;
        }
        if (order.getStatus() != OrderStatus.PENDING) {
            log.info("订单非待支付状态，跳过取消: orderId={}, status={}", orderId, order.getStatus());
            return;
        }
        order.setStatus(OrderStatus.CANCELLED);
        orderMapper.updateById(order);
        log.info("订单已取消: orderId={}", orderId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void payOrder(String orderId, Long userId) {
        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BizException(ResultCode.NOT_FOUND, "订单不存在");
        }
        if (!order.getUserId().equals(userId)) {
            throw new BizException(ResultCode.FORBIDDEN, "无权操作此订单");
        }
        if (order.getStatus() != OrderStatus.PENDING) {
            throw new BizException("订单状态不允许支付，当前状态: " + order.getStatus().getDescription());
        }
        order.setStatus(OrderStatus.PAID);
        orderMapper.updateById(order);

        syncPurchaseRecords(order);

        log.info("订单支付成功: orderId={}, userId={}", orderId, userId);
    }

    /**
     * 支付成功后，遍历订单项调用 vibe-catalog 同步购买记录，
     * 使"已购作品"列表能查到通过订单购买的商品。
     */
    private void syncPurchaseRecords(Order order) {
        var itemWrapper = new LambdaQueryWrapper<OrderItem>()
                .eq(OrderItem::getOrderId, order.getId());
        List<OrderItem> items = orderItemMapper.selectList(itemWrapper);

        for (OrderItem item : items) {
            try {
                catalogFeignClient.confirmPurchase(
                        item.getTrackId(),
                        order.getUserId(),
                        item.getPrice()
                );
            } catch (Exception e) {
                log.error("同步购买记录失败: orderId={}, trackId={}, userId={}",
                        order.getId(), item.getTrackId(), order.getUserId(), e);
            }
        }
    }

    // ==================== Private Methods ====================

    private void validateTracksAvailability(List<CartItemDTO> items) {
        for (CartItemDTO item : items) {
            try {
                Result<Map<String, Object>> result = catalogFeignClient.getTrackDetail(item.getTrackId());
                if (result == null || result.getCode() != 200 || result.getData() == null) {
                    throw new BizException("作品 [" + item.getTitle() + "] 不存在或已下架");
                }

                Map<String, Object> track = result.getData();

                // 校验上架状态
                Object statusObj = track.get("status");
                if (statusObj != null && Integer.parseInt(statusObj.toString()) != 1) {
                    throw new BizException("作品 [" + item.getTitle() + "] 未上架");
                }

                // 校验库存（EXCLUSIVE 类型需要检查库存）
                if ("EXCLUSIVE".equalsIgnoreCase(item.getLicenseType())) {
                    Object stockObj = track.get("stock");
                    if (stockObj != null) {
                        int stock = Integer.parseInt(stockObj.toString());
                        if (stock <= 0) {
                            throw new BizException(ResultCode.OUT_OF_STOCK,
                                    "作品 [" + item.getTitle() + "] 独占授权已售罄");
                        }
                    }
                }

                // 校验价格一致性
                Object priceObj = track.get("price");
                if (priceObj != null) {
                    BigDecimal remotePrice = new BigDecimal(priceObj.toString());
                    if (remotePrice.compareTo(item.getPrice()) != 0) {
                        throw new BizException("作品 [" + item.getTitle() + "] 价格已变更，请刷新购物车");
                    }
                }
            } catch (BizException e) {
                throw e;
            } catch (Exception e) {
                log.error("调用 Catalog 服务校验作品失败: trackId={}", item.getTrackId(), e);
                throw new BizException("校验作品信息失败，请稍后重试");
            }
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public Order saveOrderAndItems(Long userId, List<CartItemDTO> cartItems) {
        BigDecimal totalAmount = cartItems.stream()
                .map(CartItemDTO::getPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Order order = new Order();
        order.setUserId(userId);
        order.setTotalAmount(totalAmount);
        order.setStatus(OrderStatus.PENDING);
        orderMapper.insert(order);

        for (CartItemDTO item : cartItems) {
            OrderItem orderItem = new OrderItem();
            orderItem.setOrderId(order.getId());
            orderItem.setTrackId(item.getTrackId());
            orderItem.setLicenseType(item.getLicenseType());
            orderItem.setPrice(item.getPrice());
            orderItemMapper.insert(orderItem);
        }

        return order;
    }

    private void sendDelayedCancelMessage(String orderId) {
        MessagePostProcessor processor = message -> {
            message.getMessageProperties().setExpiration(String.valueOf(orderTimeoutMs));
            return message;
        };

        rabbitTemplate.convertAndSend(
                RabbitMQConfig.ORDER_DELAY_EXCHANGE,
                RabbitMQConfig.ORDER_DELAY_ROUTING_KEY,
                orderId,
                processor
        );
        log.debug("已发送订单延迟取消消息: orderId={}, ttl={}ms", orderId, orderTimeoutMs);
    }

    private OrderVO buildOrderVO(Order order, List<CartItemDTO> cartItems) {
        List<OrderItemVO> itemVOs = cartItems.stream()
                .map(ci -> new OrderItemVO(null, order.getId(), ci.getTrackId(), ci.getLicenseType(), ci.getPrice()))
                .toList();

        return new OrderVO(
                order.getId(),
                order.getUserId(),
                order.getTotalAmount(),
                order.getStatus().getCode(),
                order.getStatus().getDescription(),
                itemVOs,
                order.getCreateTime(),
                order.getUpdateTime()
        );
    }

    private OrderVO buildOrderVOFromDb(Order order) {
        var itemWrapper = new LambdaQueryWrapper<OrderItem>()
                .eq(OrderItem::getOrderId, order.getId());
        List<OrderItem> items = orderItemMapper.selectList(itemWrapper);

        List<OrderItemVO> itemVOs = items.stream()
                .map(oi -> new OrderItemVO(oi.getId(), oi.getOrderId(), oi.getTrackId(), oi.getLicenseType(), oi.getPrice()))
                .toList();

        return new OrderVO(
                order.getId(),
                order.getUserId(),
                order.getTotalAmount(),
                order.getStatus().getCode(),
                order.getStatus().getDescription(),
                itemVOs,
                order.getCreateTime(),
                order.getUpdateTime()
        );
    }
}
