package com.blog.utils;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.github.benmanes.caffeine.cache.Cache;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

@Component
@Slf4j
public class CacheClient {

    private final StringRedisTemplate stringRedisTemplate;

    public CacheClient(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    /**
     * 将数据加入Redis，并设置有效期
     */
    public void set(String key, Object value, Long timeout, TimeUnit unit) {
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value), timeout, unit);
    }

    /**
     * 将数据加入Redis，并设置逻辑过期时间
     */
    public void setWithLogicalExpire(String key, Object value, Long timeout, TimeUnit unit) {
        RedisData redisData = new RedisData();
        redisData.setData(value);
        // unit.toSeconds()是为了确保计时单位是秒
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(unit.toSeconds(timeout)));
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value), timeout, unit);
    }

    /**
     * 缓存空值解决缓存穿透
     *
     * @param keyPrefix  key前缀
     * @param id         查询id
     * @param type       查询的数据类型
     * @param dbFallback 根据id查询数据的函数
     * @param timeout    有效期
     * @param unit       有效期的时间单位
     * @param <T>
     * @param <ID>
     * @return
     */
    public <T, ID> T queryWithPassThrough(String keyPrefix, ID id, Class<T> type,
                                          Function<ID, T> dbFallback, Long timeout, TimeUnit unit) {
        String key = keyPrefix + id;
        // 1. 从Redis中查询数据
        String json = stringRedisTemplate.opsForValue().get(key);

        T t = null;
        // 2. 判断缓存是否命中
        if (StrUtil.isNotBlank(json)) {
            // 3. 缓存命中，直接返回数据
            return JSONUtil.toBean(json, type);
        }
        // 判断命中的是否是空值
        if (json != null) {
            // 直接返回失败信息
            return null;
        }

        // 缓存未命中, 根据id查数据库
        t = dbFallback.apply(id);

        // 4. 判断数据库是否存在店铺数据
        if (Objects.isNull(t)) {
            // 5. 数据库中不存在，缓存空值（解决缓存穿透），返回失败信息
            this.set(key, "", RedisConstants.CACHE_NULL_TTL, TimeUnit.SECONDS);
            return null;
        }
        // 6. 数据库中存在，重建缓存，并返回店铺数据
        this.set(key, t, timeout, unit);
        return t;
    }

    /**
     * 缓存重建线程池
     */
    public static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    /**
     * 逻辑过期解决缓存击穿
     *
     * @param keyPrefix  key前缀
     * @param id         查询id
     * @param type       查询的数据类型
     * @param dbFallback 根据id查询数据的函数
     * @param timeout    有效期
     * @param unit       有效期的时间单位
     * @param <T>
     * @param <ID>
     * @return
     */
    public <T, ID> T queryWithLogicExpire(String keyPrefix, ID id, Class<T> type,
                                          Function<ID, T> dbFallback, Long timeout, TimeUnit unit) {
        String key = keyPrefix + id;
        // 1、从Redis中查询店铺数据，并判断缓存是否命中
        String jsonStr = stringRedisTemplate.opsForValue().get(key);
        if (StrUtil.isBlank(jsonStr)) {
            // 1.1 缓存未命中，直接返回失败信息
            return null;
        }
        // 1.2 缓存命中，将JSON字符串反序列化未对象，并判断缓存数据是否逻辑过期
        RedisData redisData = JSONUtil.toBean(jsonStr, RedisData.class);
        JSONObject data = (JSONObject) redisData.getData();
        T t = JSONUtil.toBean(data, type);
        LocalDateTime expireTime = redisData.getExpireTime();
        if (expireTime.isAfter(LocalDateTime.now())) {
            // 当前缓存数据未过期，直接返回
            return t;
        }

        // 2、缓存数据已过期，获取互斥锁，并且重建缓存
        String lockKey = RedisConstants.LOCK_SHOP_KEY + id;
        boolean isLock = tryLock(lockKey);
        if (isLock) {
            // 获取锁成功，开启一个子线程去重建缓存
            CACHE_REBUILD_EXECUTOR.submit(() -> {
                try {
                    // 查询数据库
                    T t1 = dbFallback.apply(id);
                    // 将查询到的数据保存到Redis
                    this.setWithLogicalExpire(key, t1, timeout, unit);
                } finally {
                    unlock(lockKey);
                }
            });
        }
        // 3、返回过期数据
        return t;

    }

    // -------------------------------------------------------------------------
    // 二级缓存：L1(Caffeine) → L2(Redis) → DB
    // -------------------------------------------------------------------------

    /**
     * 二级缓存查询单个对象（缓存空值防穿透）
     *
     * <pre>
     * 读取顺序：
     *   1. L1 Caffeine：命中 → 直接返回（纳秒级）
     *   2. L2 Redis   ：命中 → 回填 L1 → 返回（毫秒级）
     *   3. DB         ：命中 → 回填 L2 & L1 → 返回；未命中 → 缓存空值防穿透
     * </pre>
     *
     * @param localCache Caffeine 本地缓存实例
     * @param keyPrefix  Redis key 前缀
     * @param id         数据 ID
     * @param type       返回对象类型
     * @param dbFallback DB 查询函数
     * @param redisTtl   Redis 缓存有效期
     * @param unit       有效期时间单位
     */
    public <T, ID> T queryWithTwoLevelCache(
            Cache<String, String> localCache,
            String keyPrefix, ID id, Class<T> type,
            Function<ID, T> dbFallback, Long redisTtl, TimeUnit unit) {

        String key = keyPrefix + id;

        // 1. 查 L1 Caffeine
        String json = localCache.getIfPresent(key);
        if (json != null) {
            return json.isEmpty() ? null : JSONUtil.toBean(json, type);
        }

        // 2. 查 L2 Redis
        String redisJson = stringRedisTemplate.opsForValue().get(key);
        if (redisJson != null) {
            // 回填 L1
            localCache.put(key, redisJson);
            return redisJson.isEmpty() ? null : JSONUtil.toBean(redisJson, type);
        }

        // 3. 查 DB
        T t = dbFallback.apply(id);
        if (t == null) {
            // 缓存空值（短 TTL），防止缓存穿透
            localCache.put(key, "");
            stringRedisTemplate.opsForValue().set(key, "", RedisConstants.CACHE_NULL_TTL, TimeUnit.SECONDS);
            return null;
        }

        String newJson = JSONUtil.toJsonStr(t);
        stringRedisTemplate.opsForValue().set(key, newJson, redisTtl, unit);
        localCache.put(key, newJson);
        return t;
    }

    /**
     * 二级缓存查询列表（缓存空列表防穿透）
     *
     * @param localCache Caffeine 本地缓存实例
     * @param keyPrefix  Redis key 前缀
     * @param id         数据 ID
     * @param elementType List 中元素的类型
     * @param dbFallback  DB 查询函数
     * @param redisTtl    Redis 缓存有效期
     * @param unit        有效期时间单位
     */
    public <T, ID> List<T> queryListWithTwoLevelCache(
            Cache<String, String> localCache,
            String keyPrefix, ID id, Class<T> elementType,
            Function<ID, List<T>> dbFallback, Long redisTtl, TimeUnit unit) {

        String key = keyPrefix + id;

        // 1. 查 L1 Caffeine
        String json = localCache.getIfPresent(key);
        if (json != null) {
            return json.isEmpty() ? List.of() : JSONUtil.toList(json, elementType);
        }

        // 2. 查 L2 Redis
        String redisJson = stringRedisTemplate.opsForValue().get(key);
        if (redisJson != null) {
            localCache.put(key, redisJson);
            return redisJson.isEmpty() ? List.of() : JSONUtil.toList(redisJson, elementType);
        }

        // 3. 查 DB
        List<T> list = dbFallback.apply(id);
        if (list == null || list.isEmpty()) {
            localCache.put(key, "");
            stringRedisTemplate.opsForValue().set(key, "", RedisConstants.CACHE_NULL_TTL, TimeUnit.SECONDS);
            return List.of();
        }

        String newJson = JSONUtil.toJsonStr(list);
        stringRedisTemplate.opsForValue().set(key, newJson, redisTtl, unit);
        localCache.put(key, newJson);
        return list;
    }

    /**
     * 获取锁
     */
    private boolean tryLock(String key) {
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
        // 拆箱要判空，防止NPE
        return BooleanUtil.isTrue(flag);
    }

    /**
     * 释放锁
     */
    private void unlock(String key) {
        stringRedisTemplate.delete(key);
    }
}
