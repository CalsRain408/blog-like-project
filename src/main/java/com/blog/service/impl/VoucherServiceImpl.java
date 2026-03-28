package com.blog.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.blog.dto.Result;
import com.blog.entity.SeckillVoucher;
import com.blog.entity.Voucher;
import com.blog.mapper.VoucherMapper;
import com.blog.service.ISeckillVoucherService;
import com.blog.service.IVoucherService;
import com.blog.utils.CacheClient;
import com.blog.utils.RedisConstants;
import com.github.benmanes.caffeine.cache.Cache;
import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
public class VoucherServiceImpl extends ServiceImpl<VoucherMapper, Voucher> implements IVoucherService {

    @Resource
    private ISeckillVoucherService seckillVoucherService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private CacheClient cacheClient;

    /**
     * L1 本地缓存（Caffeine），由 CaffeineConfig 注入。
     * key = Redis cache key 全称；value = JSON 字符串。
     * 写入后 5 分钟自动过期，仅依赖 TTL 保证数据最终一致性。
     */
    @Resource(name = "voucherLocalCache")
    private Cache<String, String> voucherLocalCache;
    @Autowired
    private VoucherMapper voucherMapper;

    /**
     * 查询店铺的优惠券列表（二级缓存：L1 Caffeine → L2 Redis → DB）
     */
    @Override
    public Result queryVoucherOfShop(Long shopId) {
        List<Voucher> vouchers = cacheClient.queryListWithTwoLevelCache(
                voucherLocalCache,
                RedisConstants.CACHE_VOUCHER_SHOP_KEY,
                shopId,
                Voucher.class,
                id -> getBaseMapper().queryVoucherOfShop(id),
                RedisConstants.CACHE_VOUCHER_SHOP_TTL,
                TimeUnit.MINUTES
        );
        return Result.ok(vouchers);
    }

    /**
     * 查询单张优惠券详情（二级缓存：L1 Caffeine → L2 Redis → DB）
     */
    @Override
    public Result queryVoucherById(Long voucherId) {
        Voucher voucher = cacheClient.queryWithTwoLevelCache(
                voucherLocalCache,
                RedisConstants.CACHE_VOUCHER_KEY,
                voucherId,
                Voucher.class,
                id -> getBaseMapper().queryVoucherById(id),
                RedisConstants.CACHE_VOUCHER_TTL,
                TimeUnit.MINUTES
        );
        if (voucher == null) {
            return Result.fail("优惠券不存在");
        }
        return Result.ok(voucher);
    }

    @Override
    public List<Voucher> findVoucherByShopName(String shopName) {
        return voucherMapper.findVoucherByShopName(shopName);
    }

    @Override
    public List<Voucher> findVoucherByUserPhone(String userPhone) {
        return List.of();
    }


    /**
     * 新增秒杀优惠券：写 DB → 写 Redis 秒杀库存 key
     * 缓存一致性完全依赖 Caffeine 的 TTL 自动过期，无需主动失效。
     */
    @Override
    @Transactional
    public void addSeckillVoucher(Voucher voucher) {
        // 1. 保存优惠券至 DB
        save(voucher);

        // 2. 保存秒杀信息至 DB
        SeckillVoucher seckillVoucher = new SeckillVoucher();
        seckillVoucher.setVoucherId(voucher.getId());
        seckillVoucher.setStock(voucher.getStock());
        seckillVoucher.setBeginTime(voucher.getBeginTime());
        seckillVoucher.setEndTime(voucher.getEndTime());
        seckillVoucherService.save(seckillVoucher);

        // 3. 将秒杀库存写入 Redis（Lua 脚本扣减库存时依赖此 key）
        stringRedisTemplate.opsForValue().set(
                RedisConstants.SECKILL_STOCK_KEY + voucher.getId(),
                voucher.getStock().toString()
        );
    }
}
