package com.blog.mq;

import com.blog.entity.VoucherOrder;
import com.blog.service.IVoucherOrderService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

/**
 * 秒杀订单 Kafka 消费者
 *
 * <p>消费 {@code seckill-order} topic 中的订单消息，
 * 调用 {@link IVoucherOrderService#handleVoucherOrderMessage} 完成
 * DB 库存扣减和订单持久化。
 *
 * <p>手动提交 offset（{@code enable-auto-commit: false}）：
 * 只有业务逻辑执行成功后才调用 {@link Acknowledgment#acknowledge()}，
 * 保证消息至少被处理一次（at-least-once）。
 * 若处理抛出异常，offset 不提交，Kafka 将按 {@link com.blog.config.KafkaConfig}
 * 中配置的重试策略进行重试。
 */
@Component
@Slf4j
public class VoucherOrderConsumer {

    @Resource
    private IVoucherOrderService voucherOrderService;

    @KafkaListener(
            topics = "seckill-order",
            groupId = "seckill-order-group",
            // 并发消费者数量 = 分区数，充分利用 3 个分区
            concurrency = "3"
    )
    public void onMessage(VoucherOrder voucherOrder, Acknowledgment ack) {
        log.debug("消费秒杀订单消息，orderId={}, voucherId={}, userId={}",
                voucherOrder.getId(), voucherOrder.getVoucherId(), voucherOrder.getUserId());
        try {
            voucherOrderService.handleVoucherOrderMessage(voucherOrder);
            // 业务成功，手动提交 offset
            ack.acknowledge();
        } catch (Exception e) {
            log.error("处理秒杀订单失败，orderId={}", voucherOrder.getId(), e);
            // 不提交 offset，交由 DefaultErrorHandler 重试
            throw e;
        }
    }
}
