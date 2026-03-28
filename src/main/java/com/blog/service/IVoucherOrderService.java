package com.blog.service;


import com.baomidou.mybatisplus.extension.service.IService;
import com.blog.dto.Result;
import com.blog.entity.VoucherOrder;


public interface IVoucherOrderService extends IService<VoucherOrder> {
    Result seckillVoucher(Long voucherId);

    Result createVoucherOrder(Long voucherId);
}
