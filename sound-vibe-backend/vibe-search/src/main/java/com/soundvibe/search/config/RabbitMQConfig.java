package com.soundvibe.search.config;

import org.springframework.amqp.core.Queue;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ 配置
 * 声明队列并配置 JSON 消息转换器
 *
 * @author SoundVibe Team
 */
@Configuration
public class RabbitMQConfig {

    /**
     * 作品同步队列名称常量
     */
    public static final String TRACK_SYNC_QUEUE = "soundvibe.track.sync.queue";

    /**
     * 声明作品同步队列
     * durable=true 持久化，服务重启后队列不丢失
     */
    @Bean
    public Queue trackSyncQueue() {
        return new Queue(TRACK_SYNC_QUEUE, true);
    }

    /**
     * JSON 消息转换器
     * 统一使用 Jackson 进行消息的序列化/反序列化
     */
    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
