package com.blog.controller;

import com.blog.annotation.RateLimiter;
import com.blog.dto.Result;
import com.blog.service.IVoucherOrderService;
import com.blog.utils.RedisConstants;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


@RestController
@RequestMapping("/voucher-order")
public class VoucherOrderController {

    @Resource
    private IVoucherOrderService voucherOrderService;

    @PostMapping("seckill/{id}")
    @RateLimiter(
            key = RedisConstants.SLIDE_WINDOW_KEY,
            window = 10,
            limit = 100,
            message = "秒杀活动太火爆，请稍后再试",
            type = RateLimiter.LimitType.METHOD
    )  // 全局
    public Result seckillVoucher(@PathVariable("id") Long voucherId) {
        return voucherOrderService.seckillVoucher(voucherId);
    }
}
