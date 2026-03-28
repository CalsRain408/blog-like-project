package com.blog.service;


import com.baomidou.mybatisplus.extension.service.IService;
import com.blog.dto.Result;
import com.blog.entity.VoucherOrder;


public interface IVoucherOrderService extends IService<VoucherOrder> {
    Result seckillVoucher(Long voucherId);

    Result createVoucherOrder(Long voucherId);

    /**
     * 处理 Kafka 消费到的秒杀订单消息：扣减 DB 库存 + 持久化订单
     */
    void handleVoucherOrderMessage(VoucherOrder voucherOrder);
}
