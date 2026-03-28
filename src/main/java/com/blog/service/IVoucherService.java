package com.blog.service;


import com.baomidou.mybatisplus.extension.service.IService;
import com.blog.dto.Result;
import com.blog.entity.Voucher;


public interface IVoucherService extends IService<Voucher> {

    Result queryVoucherOfShop(Long shopId);

    void addSeckillVoucher(Voucher voucher);

    Result queryVoucherById(Long voucherId);
}
