package com.blog.mq;

import com.blog.dto.StockReleaseMessage;
import com.blog.service.IOrderService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

/**
 * 库存释放重试消费者
 *
 * <p>消费 {@code stock-release} topic 中的消息，对关单后释放库存失败的优惠券
 * 再次执行 stock + 1 操作。手动提交 offset，保证 at-least-once。
 * 若重试仍失败，由 {@link com.blog.config.KafkaConfig} 中的
 * {@code DefaultErrorHandler} 按 FixedBackOff 策略再次重试，最终进入死信队列。
 */
@Component
@Slf4j
public class StockReleaseConsumer {

    @Resource
    private IOrderService orderService;

    @KafkaListener(
            topics = "stock-release",
            groupId = "stock-release-group"
    )
    public void onMessage(StockReleaseMessage msg, Acknowledgment ack) {
        log.info("消费库存释放消息，orderId={}, voucherId={}", msg.getOrderId(), msg.getVoucherId());
        try {
            boolean released = orderService.releaseStock(msg.getVoucherId());
            if (released) {
                log.info("库存释放重试成功，voucherId={}", msg.getVoucherId());
            } else {
                log.error("库存释放重试仍失败，voucherId={}，将由 ErrorHandler 继续重试", msg.getVoucherId());
                // 抛出异常触发 DefaultErrorHandler 重试
                throw new RuntimeException("库存释放失败，voucherId=" + msg.getVoucherId());
            }
            ack.acknowledge();
        } catch (RuntimeException e) {
            // 不提交 offset，由 DefaultErrorHandler 按 FixedBackOff 重试
            throw e;
        }
    }
}
