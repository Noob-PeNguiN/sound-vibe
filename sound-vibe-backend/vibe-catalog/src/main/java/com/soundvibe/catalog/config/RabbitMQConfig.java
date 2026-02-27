package com.soundvibe.catalog.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ 配置
 * 1. Producer: 作品同步队列 → vibe-search
 * 2. Consumer: 音频分析完成通知 ← vibe-analysis (Python)
 *
 * @author SoundVibe Team
 */
@Configuration
public class RabbitMQConfig {

    public static final String TRACK_SYNC_QUEUE = "soundvibe.track.sync.queue";

    /**
     * vibe-analysis 使用的 Topic Exchange（与 vibe-asset 定义一致）
     */
    public static final String ASSET_TOPIC_EXCHANGE = "soundvibe.asset.topic";

    /**
     * 分析完成通知队列（本服务消费）
     */
    public static final String ANALYSIS_COMPLETED_QUEUE = "soundvibe.asset.analysis.completed.queue";

    /**
     * 分析完成路由键（Python worker 发送）
     */
    public static final String ROUTING_KEY_ANALYSIS_COMPLETED = "asset.analysis.completed";

    @Bean
    public Queue trackSyncQueue() {
        return new Queue(TRACK_SYNC_QUEUE, true);
    }

    @Bean
    public TopicExchange assetTopicExchange() {
        return ExchangeBuilder
                .topicExchange(ASSET_TOPIC_EXCHANGE)
                .durable(true)
                .build();
    }

    @Bean
    public Queue analysisCompletedQueue() {
        return QueueBuilder
                .durable(ANALYSIS_COMPLETED_QUEUE)
                .build();
    }

    @Bean
    public Binding analysisCompletedBinding(Queue analysisCompletedQueue, TopicExchange assetTopicExchange) {
        return BindingBuilder
                .bind(analysisCompletedQueue)
                .to(assetTopicExchange)
                .with(ROUTING_KEY_ANALYSIS_COMPLETED);
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
