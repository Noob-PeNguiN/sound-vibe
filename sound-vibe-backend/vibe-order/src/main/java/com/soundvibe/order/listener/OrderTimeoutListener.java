package com.soundvibe.order.listener;

import com.rabbitmq.client.Channel;
import com.soundvibe.order.config.RabbitMQConfig;
import com.soundvibe.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 订单超时取消监听器
 * <p>
 * 监听死信队列 order.cancel.queue，当订单超过 15 分钟未支付时
 * 自动将订单状态从 PENDING 更新为 CANCELLED
 *
 * @author SoundVibe Team
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderTimeoutListener {

    private final OrderService orderService;

    @RabbitListener(queues = RabbitMQConfig.ORDER_CANCEL_QUEUE)
    public void onOrderTimeout(Message message, Channel channel) throws IOException {
        String orderId = new String(message.getBody());
        long deliveryTag = message.getMessageProperties().getDeliveryTag();

        try {
            log.info("收到订单超时取消消息: orderId={}", orderId);
            orderService.cancelOrder(orderId);
            channel.basicAck(deliveryTag, false);
        } catch (Exception e) {
            log.error("处理订单超时取消失败: orderId={}", orderId, e);
            channel.basicNack(deliveryTag, false, true);
        }
    }
}
