package com.soundvibe.order.config;

import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ 配置 — 订单延迟取消（基于 Dead Letter Exchange）
 * <p>
 * 工作流程：
 * 1. 下单时发送消息到 order.delay.exchange → order.delay.queue（设置 TTL）
 * 2. 消息在 delay queue 中过期后，通过 DLX 路由到 order.cancel.exchange → order.cancel.queue
 * 3. OrderTimeoutListener 监听 order.cancel.queue，执行订单取消逻辑
 *
 * @author SoundVibe Team
 */
@Configuration
public class RabbitMQConfig {

    public static final String ORDER_DELAY_EXCHANGE = "order.delay.exchange";
    public static final String ORDER_DELAY_QUEUE = "order.delay.queue";
    public static final String ORDER_DELAY_ROUTING_KEY = "order.delay";

    public static final String ORDER_CANCEL_EXCHANGE = "order.cancel.exchange";
    public static final String ORDER_CANCEL_QUEUE = "order.cancel.queue";
    public static final String ORDER_CANCEL_ROUTING_KEY = "order.cancel";

    // ==================== 死信交换机 & 队列 (取消) ====================

    @Bean
    public DirectExchange orderCancelExchange() {
        return new DirectExchange(ORDER_CANCEL_EXCHANGE, true, false);
    }

    @Bean
    public Queue orderCancelQueue() {
        return QueueBuilder.durable(ORDER_CANCEL_QUEUE).build();
    }

    @Bean
    public Binding orderCancelBinding() {
        return BindingBuilder.bind(orderCancelQueue())
                .to(orderCancelExchange())
                .with(ORDER_CANCEL_ROUTING_KEY);
    }

    // ==================== 延迟交换机 & 队列 ====================

    @Bean
    public DirectExchange orderDelayExchange() {
        return new DirectExchange(ORDER_DELAY_EXCHANGE, true, false);
    }

    @Bean
    public Queue orderDelayQueue() {
        return QueueBuilder.durable(ORDER_DELAY_QUEUE)
                .deadLetterExchange(ORDER_CANCEL_EXCHANGE)
                .deadLetterRoutingKey(ORDER_CANCEL_ROUTING_KEY)
                .build();
    }

    @Bean
    public Binding orderDelayBinding() {
        return BindingBuilder.bind(orderDelayQueue())
                .to(orderDelayExchange())
                .with(ORDER_DELAY_ROUTING_KEY);
    }
}
