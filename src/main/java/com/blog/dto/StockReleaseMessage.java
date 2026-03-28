package com.blog.dto;

import lombok.Data;

/**
 * 库存释放重试消息（发往 Kafka topic: stock-release）
 */
@Data
public class StockReleaseMessage {
    /** 关闭的订单 ID（用于日志追踪） */
    private Long orderId;
    /** 需要归还库存的优惠券 ID */
    private Long voucherId;
}
