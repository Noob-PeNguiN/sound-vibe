package com.soundvibe.asset.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ 配置类
 * 定义音频资产事件的 Exchange、Queue 和 Binding
 * <p>
 * 拓扑结构:
 *   Producer → [soundvibe.asset.topic] → (asset.uploaded) → [soundvibe.asset.analysis.queue]
 *
 * @author SoundVibe Team
 */
@Configuration
public class RabbitMQConfig {

    /**
     * Topic Exchange 名称
     */
    public static final String ASSET_TOPIC_EXCHANGE = "soundvibe.asset.topic";

    /**
     * 分析队列名称
     */
    public static final String ASSET_ANALYSIS_QUEUE = "soundvibe.asset.analysis.queue";

    /**
     * 路由键: 资产上传完成
     */
    public static final String ROUTING_KEY_ASSET_UPLOADED = "asset.uploaded";

    /**
     * 声明 Topic Exchange
     * durable=true: 持久化，RabbitMQ 重启后仍存在
     */
    @Bean
    public TopicExchange assetTopicExchange() {
        return ExchangeBuilder
                .topicExchange(ASSET_TOPIC_EXCHANGE)
                .durable(true)
                .build();
    }

    /**
     * 声明分析队列
     * durable=true: 队列持久化
     */
    @Bean
    public Queue assetAnalysisQueue() {
        return QueueBuilder
                .durable(ASSET_ANALYSIS_QUEUE)
                .build();
    }

    /**
     * 绑定队列到 Exchange
     * routing key: asset.uploaded
     */
    @Bean
    public Binding assetAnalysisBinding(Queue assetAnalysisQueue, TopicExchange assetTopicExchange) {
        return BindingBuilder
                .bind(assetAnalysisQueue)
                .to(assetTopicExchange)
                .with(ROUTING_KEY_ASSET_UPLOADED);
    }

    /**
     * JSON 消息转换器
     * 自动将 Java 对象序列化为 JSON 发送
     */
    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
