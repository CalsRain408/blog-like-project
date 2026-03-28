package com.blog.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * Caffeine 本地缓存配置（二级缓存 L1）
 *
 * <p>读写顺序：L1(Caffeine) → L2(Redis) → DB
 * <ul>
 *   <li>maximumSize：最多缓存 1000 个条目，超出按 LRU 淘汰</li>
 *   <li>expireAfterWrite：写入 5 分钟后自动失效，防止内存中保留过期数据</li>
 * </ul>
 * value 统一存储 JSON 字符串，与 Redis 层保持一致，反序列化逻辑只需在一处维护。
 */
@Configuration
public class CaffeineConfig {

    /**
     * 优惠券详情 & 店铺优惠券列表 共用的本地缓存
     * key   : Redis cache key（含前缀，如 "cache:voucher:1" / "cache:voucher:shop:2"）
     * value : JSON 字符串
     */
    @Bean("voucherLocalCache")
    public Cache<String, String> voucherLocalCache() {
        return Caffeine.newBuilder()
                .maximumSize(1000)
                .expireAfterWrite(5, TimeUnit.MINUTES)
                .build();
    }
}
