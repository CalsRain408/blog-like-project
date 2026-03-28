package com.blog.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

/**
 * Kafka 配置：Topic 定义 & 消费者错误处理策略
 */
@Configuration
public class KafkaConfig {

    /** 秒杀订单 Topic：3 个分区（支持并发消费），1 副本（本地开发环境） */
    @Bean
    public NewTopic seckillOrderTopic() {
        return TopicBuilder.name("seckill-order")
                .partitions(3)
                .replicas(1)
                .build();
    }

    /**
     * 消费者错误处理：最多重试 3 次，每次间隔 1 秒。
     * 重试耗尽后消息进入死信队列（需配置 DLT），避免阻塞正常消费。
     */
    @Bean
    public DefaultErrorHandler kafkaErrorHandler() {
        // 最多重试 3 次，间隔 1000ms
        return new DefaultErrorHandler(new FixedBackOff(1000L, 3L));
    }
}
