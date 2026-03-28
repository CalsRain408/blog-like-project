package com.blog.service;

public interface IOrderService {

    /**
     * 扫描并关闭超时未支付订单，关单后释放库存
     *
     * @param orderExpireMinutes 订单超时时间（分钟）
     */
    void closeExpiredOrders(int orderExpireMinutes);

    /**
     * CAS 关闭单笔订单：仅当 status=1（未支付）时才更新为 status=4（已取消）
     *
     * @param orderId 订单 ID
     * @return true=关闭成功；false=订单已支付或已被关闭，无需处理
     */
    boolean closeOrder(Long orderId);

    /**
     * 释放秒杀券库存（stock + 1）
     *
     * @param voucherId 优惠券 ID
     * @return true=释放成功；false=释放失败（需 Kafka 重试）
     */
    boolean releaseStock(Long voucherId);
}
