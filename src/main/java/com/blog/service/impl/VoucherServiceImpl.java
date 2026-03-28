package com.blog.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

import com.blog.dto.Result;
import com.blog.entity.SeckillVoucher;
import com.blog.entity.Voucher;
import com.blog.mapper.VoucherMapper;
import com.blog.service.ISeckillVoucherService;
import com.blog.service.IVoucherService;
import com.blog.utils.RedisConstants;
import jakarta.annotation.Resource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


import java.util.List;


@Service
public class VoucherServiceImpl extends ServiceImpl<VoucherMapper, Voucher> implements IVoucherService {

    @Resource
    private ISeckillVoucherService seckillVoucherService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result queryVoucherOfShop(Long shopId) {
        // 查询优惠券信息
        List<Voucher> vouchers = getBaseMapper().queryVoucherOfShop(shopId);
        // 返回结果
        return Result.ok(vouchers);
    }

    @Override
    @Transactional
    public void addSeckillVoucher(Voucher voucher) {
        // 保存优惠券
        save(voucher);
        // 保存秒杀信息
        SeckillVoucher seckillVoucher = new SeckillVoucher();
        seckillVoucher.setVoucherId(voucher.getId());
        seckillVoucher.setStock(voucher.getStock());
        seckillVoucher.setBeginTime(voucher.getBeginTime());
        seckillVoucher.setEndTime(voucher.getEndTime());
        seckillVoucherService.save(seckillVoucher);

        // 将优惠券信息保存至Redis中
        // key: 业务前缀+优惠券id value: 库存
        stringRedisTemplate.opsForValue().set(
                RedisConstants.SECKILL_STOCK_KEY + voucher.getId(),
                voucher.getStock().toString()
        );
    }
}
