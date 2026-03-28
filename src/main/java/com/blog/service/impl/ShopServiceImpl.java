package com.blog.service.impl;


import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.blog.dto.Result;
import com.blog.entity.Shop;
import com.blog.entity.ShopType;
import com.blog.mapper.ShopMapper;
import com.blog.service.IShopService;
import com.blog.utils.CacheClient;
import com.blog.utils.RedisConstants;
import com.blog.utils.RedisData;
import com.blog.utils.SystemConstants;
import jakarta.annotation.Resource;
import org.springframework.boot.autoconfigure.cache.CacheProperties;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;


@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    // 线程池
    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private CacheClient cacheClient;

    @Override
    public Result queryById(Long id) {
        Shop shop = cacheClient.queryWithLogicExpire(
                RedisConstants.CACHE_SHOP_KEY, id, Shop.class, this::getById,
                RedisConstants.CACHE_SHOP_TTL, TimeUnit.SECONDS
        );

        return shop != null ? Result.ok(shop) : Result.fail("店铺不存在");
    }

    // 互斥锁解决缓存击穿
    private Shop queryWithMutex(Long id){
        // 1. 先查Redis缓存中的数据
        String key = RedisConstants.CACHE_SHOP_KEY + id;
        String shopJson = stringRedisTemplate.opsForValue().get(key);

        Shop shop = null;

        // 2. 判断缓存是否命中
        if (shopJson != null){
            // 2.1 命中
            if (!shopJson.isEmpty()){
                // 2.1.1 命中且不是空值
                shop = JSONUtil.toBean(shopJson, Shop.class);
                return shop;
            }
            else{
                // 2.1.2 空值就直接返回
                return null;
            }
        }

        // 2.2 未命中，尝试获取互斥锁
        String lockKey = RedisConstants.LOCK_SHOP_KEY + id;
        try {
            boolean isLock = tryLock(lockKey);
            if (!isLock) {
                // 获取失败，已经有线程在重建缓存，进入休眠状态
                Thread.sleep(50);
                return queryWithMutex(id);  // 从头开始
            }

            // 获取成功，根据id查询数据库
            shop = this.getById(id);
            // 模拟复杂缓存重建延时
            Thread.sleep(200);

            if (shop == null){
                // 向Redis中缓存一个空值，解决缓存穿透
                stringRedisTemplate.opsForValue().set(key, "", RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);
                return null;
            }

            // 存在还需要写入Redis，重建缓存
            stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop), RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
                throw new RuntimeException(e);
        } finally {
            // 释放互斥锁
            unlock(lockKey);
        }

        return shop;
    }

    // 逻辑过期解决缓存击穿
    private Shop queryWithLogicExpire(Long id){
        String key = RedisConstants.CACHE_SHOP_KEY + id;
        String shopJson = stringRedisTemplate.opsForValue().get(key);

        // 2. 判断缓存是否命中
        if (StrUtil.isBlank(shopJson)){
            // 没命中，直接返回
            return null;
        }

        // 命中，查看过期时间
        RedisData redisData = JSONUtil.toBean(shopJson, RedisData.class);
        LocalDateTime expireTime = redisData.getExpireTime();
        JSONObject jsonObject = (JSONObject) redisData.getData();
        Shop shop = JSONUtil.toBean(jsonObject, Shop.class);

        // 判断是否过期
        if (expireTime.isAfter(LocalDateTime.now())){
            // 没有过期，返回信息
            return shop;
        }

        // 过期了，重建缓存
        String lockKey = RedisConstants.LOCK_SHOP_KEY + id;
        boolean isLock = tryLock(lockKey);
        if (isLock){
            // 获取互斥锁成功 开启独立线程，实现缓存重建
            CACHE_REBUILD_EXECUTOR.submit(() -> {
                try {
                    this.saveShop2Redis(id, 20L);
                } catch (Exception e){
                    throw new RuntimeException(e);
                } finally {
                    unlock(lockKey);
                }
            });

        }

        // 返回过期的店铺信息
        return shop;
    }

    private void saveShop2Redis(Long id, long expireSeconds) throws InterruptedException {
        // 从数据库中再获取，进行数据预热
        Shop shop = this.getById(id);
        Thread.sleep(200);

        RedisData redisData = new RedisData();
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireSeconds));
        redisData.setData(shop);

        stringRedisTemplate.opsForValue()
                .set(RedisConstants.CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(redisData));
    }

    /**
     * 释放互斥锁
     * @param key
     */
    private void unlock(String key) {
        stringRedisTemplate.delete(key);
    }

    /**
     * 获取锁
     * @param key
     * @return
     */
    private boolean tryLock(String key) {
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);

        // 拆箱（Unboxing）：指将包装类型（如 Boolean）转换为对应的基本类型（如 boolean）
        // 拆箱之前要判空，防止空指针异常(NPE)
        return BooleanUtil.isTrue(flag);
    }

    @Override
    public Result updateShop(Shop shop) {
        this.updateById(shop);

        // 删除缓存
        stringRedisTemplate.delete(RedisConstants.CACHE_SHOP_KEY + shop.getId());
        return Result.ok();
    }

}
