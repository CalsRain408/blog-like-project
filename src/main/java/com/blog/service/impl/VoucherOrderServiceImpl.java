package com.blog.service.impl;


import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.blog.dto.Result;
import com.blog.entity.VoucherOrder;
import com.blog.mapper.VoucherOrderMapper;
import com.blog.service.ISeckillVoucherService;
import com.blog.service.IVoucherOrderService;
import com.blog.utils.RedisIdWorker;
import com.blog.utils.UserHolder;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;

@Service
@Slf4j
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Resource
    private ISeckillVoucherService seckillVoucherService;

    @Resource
    private RedisIdWorker redisIdWorker;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private KafkaTemplate<String, VoucherOrder> kafkaTemplate;

    private static final String TOPIC_SECKILL_ORDER = "seckill-order";

    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;
    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }

    /**
     * 抢购秒杀券
     *
     * <ol>
     *   <li>执行 Lua 脚本：在 Redis 中原子校验库存 & 一人一单</li>
     *   <li>校验通过后将订单消息发送至 Kafka topic {@code seckill-order}，立即返回订单 ID</li>
     *   <li>Kafka 消费者异步完成 DB 库存扣减和订单入库</li>
     * </ol>
     */
    @Override
    public Result seckillVoucher(Long voucherId) {
        Long userId = UserHolder.getUser().getId();

        // 1. 执行 Lua 脚本，校验秒杀资格
        Long result = stringRedisTemplate.execute(
                SECKILL_SCRIPT,
                Collections.emptyList(),
                voucherId.toString(),
                userId.toString()
        );
        if (result == 1) {
            return Result.fail("库存不足");
        }
        if (result == 2) {
            return Result.fail("重复下单");
        }

        // 2. 构造订单对象
        long orderId = redisIdWorker.nextId("order");
        VoucherOrder voucherOrder = new VoucherOrder();
        voucherOrder.setId(orderId);
        voucherOrder.setUserId(userId);
        voucherOrder.setVoucherId(voucherId);

        // 3. 发送至 Kafka，以 voucherId 为 key 保证同一券的订单落入同一分区
        kafkaTemplate.send(TOPIC_SECKILL_ORDER, voucherId.toString(), voucherOrder);

        return Result.ok(orderId);
    }

    /**
     * 处理 Kafka 消费到的秒杀订单消息：扣减 DB 库存 + 持久化订单。
     * 在 Redis 已完成库存校验的前提下，此处仅做兜底的 stock > 0 判断。
     */
    @Override
    @Transactional
    public void handleVoucherOrderMessage(VoucherOrder voucherOrder) {
        boolean success = seckillVoucherService.update()
                .setSql("stock = stock - 1")
                .eq("voucher_id", voucherOrder.getVoucherId())
                .gt("stock", 0)
                .update();
        if (!success) {
            log.error("库存扣减失败，orderId={}, voucherId={}",
                    voucherOrder.getId(), voucherOrder.getVoucherId());
            return;
        }
        save(voucherOrder);
    }

    @Transactional
    public Result createVoucherOrder(Long voucherId) {
        Long userId = UserHolder.getUser().getId();

        int count = Math.toIntExact(query()
                .eq("user_id", userId)
                .eq("voucher_id", voucherId)
                .count());

        if (count > 0) {
            return Result.fail("用户已购买过该优惠券");
        }

        boolean success = seckillVoucherService.update()
                .setSql("stock = stock -1")
                .eq("voucher_id", voucherId)
                .gt("stock", 0)
                .update();
        if (!success) {
            throw new RuntimeException("秒杀券扣减库存失败");
        }

        VoucherOrder voucherOrder = new VoucherOrder();
        long orderId = redisIdWorker.nextId("order");
        voucherOrder.setId(orderId);
        voucherOrder.setUserId(userId);
        voucherOrder.setVoucherId(voucherId);
        save(voucherOrder);
        return Result.ok(orderId);
    }
}
