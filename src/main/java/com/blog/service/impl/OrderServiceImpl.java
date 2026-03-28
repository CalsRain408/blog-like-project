package com.blog.service.impl;

import com.blog.dto.StockReleaseMessage;
import com.blog.entity.SeckillVoucher;
import com.blog.entity.VoucherOrder;
import com.blog.service.IOrderService;
import com.blog.service.ISeckillVoucherService;
import com.blog.service.IVoucherOrderService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@Slf4j
public class OrderServiceImpl implements IOrderService {

    private static final String TOPIC_STOCK_RELEASE = "stock-release";

    @Resource
    private IVoucherOrderService voucherOrderService;

    @Resource
    private ISeckillVoucherService seckillVoucherService;

    @Resource
    private KafkaTemplate<String, StockReleaseMessage> kafkaTemplate;

    /**
     * 自注入：使 closeOrder/releaseStock 的调用经过 Spring 代理，保证 @Transactional 生效。
     * 用 @Lazy 打破初始化时的循环依赖。
     */
    @Lazy
    @Resource
    private IOrderService self;

    /**
     * 扫描超时未支付订单，依次执行关单 → 释放库存，每笔订单独立处理：
     * <ol>
     *   <li>CAS 关单（status 1 → 4），防止并发重复处理</li>
     *   <li>关单成功后释放库存（stock + 1）</li>
     *   <li>库存释放失败时，发 Kafka 消息到 {@code stock-release} topic 进行重试</li>
     * </ol>
     */
    @Override
    public void closeExpiredOrders(int orderExpireMinutes) {
        LocalDateTime expireTime = LocalDateTime.now().minusMinutes(orderExpireMinutes);

        List<VoucherOrder> timeoutOrders = voucherOrderService.lambdaQuery()
                .eq(VoucherOrder::getStatus, 1)
                .lt(VoucherOrder::getCreateTime, expireTime)
                .list();

        if (timeoutOrders.isEmpty()) {
            log.debug("暂无超时未支付订单");
            return;
        }

        log.info("扫描到 {} 笔超时未支付订单，开始关闭处理", timeoutOrders.size());

        for (VoucherOrder order : timeoutOrders) {
            // 步骤一：CAS 关单（独立事务）
            boolean closed = self.closeOrder(order.getId());
            if (!closed) {
                // 订单已支付或已被其他线程关闭，跳过
                log.debug("订单 {} 无需关闭（已支付或已关闭）", order.getId());
                continue;
            }

            log.info("订单 {} 关闭成功，尝试释放优惠券 {} 库存", order.getId(), order.getVoucherId());

            // 步骤二：释放库存（独立事务，与关单解耦）
            boolean released = self.releaseStock(order.getVoucherId());
            if (!released) {
                log.warn("订单 {} 库存释放失败，发送 Kafka 重试消息，voucherId={}",
                        order.getId(), order.getVoucherId());
                StockReleaseMessage msg = new StockReleaseMessage();
                msg.setOrderId(order.getId());
                msg.setVoucherId(order.getVoucherId());
                // 以 voucherId 为 key，保证同一券的释放消息落入同一分区（有序）
                kafkaTemplate.send(TOPIC_STOCK_RELEASE, order.getVoucherId().toString(), msg);
            }
        }
    }

    /**
     * CAS（乐观锁）关闭单笔订单：UPDATE status=4 WHERE id=? AND status=1。
     * 行级锁保证同一订单不被并发重复关闭。
     *
     * @return true=关闭成功；false=订单已非未支付状态，无需处理
     */
    @Override
    @Transactional
    public boolean closeOrder(Long orderId) {
        return voucherOrderService.lambdaUpdate()
                .set(VoucherOrder::getStatus, 4)
                .set(VoucherOrder::getUpdateTime, LocalDateTime.now())
                .eq(VoucherOrder::getId, orderId)
                .eq(VoucherOrder::getStatus, 1)
                .update();
    }

    /**
     * 将指定优惠券的库存加一（stock = stock + 1）。
     *
     * @return true=释放成功；false=更新无效行（需 Kafka 重试）
     */
    @Override
    @Transactional
    public boolean releaseStock(Long voucherId) {
        return seckillVoucherService.lambdaUpdate()
                .setSql("stock = stock + 1")
                .eq(SeckillVoucher::getVoucherId, voucherId)
                .update();
    }
}
