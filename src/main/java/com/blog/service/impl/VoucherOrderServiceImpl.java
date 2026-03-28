package com.blog.service.impl;


import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.blog.dto.Result;
import com.blog.entity.SeckillVoucher;
import com.blog.entity.VoucherOrder;
import com.blog.mapper.VoucherOrderMapper;
import com.blog.service.ISeckillVoucherService;
import com.blog.service.IVoucherOrderService;
import com.blog.utils.RedisIdWorker;
import com.blog.utils.SimpleRedisLock;
import com.blog.utils.UserHolder;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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
    private RedissonClient redissonClient;

    // Lua脚本
    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;
    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }

    // 存储订单的阻塞队列,参数为队列长度
    private BlockingQueue<VoucherOrder> orderTasks = new ArrayBlockingQueue<>(1024 * 1024);
    // 执行任务的线程池, ctrl+shift+U 转换大写
    private static final ExecutorService SECKILL_ORDER_EXECUTOR = Executors.newSingleThreadExecutor();
    // 任务
    private class VoucherOrderHandler implements Runnable {
        @Override
        public void run() {
            while (true) { // 并不会对CPU造成负担,因为下面有take
                // 从阻塞队列中获取订单信息，完成库存扣减和订单生成
                try {
                    // take() 获取和删除该队列的头部,如果需要则等待直到元素可用
                    VoucherOrder voucherOrder = orderTasks.take();
                    handleVoucherOrder(voucherOrder);
                } catch (Exception e) {
                    log.error("处理订单异常", e);
                }
            }
        }
    }

    // 完成库存扣减和订单生成，数据库中的操作
    private void handleVoucherOrder(VoucherOrder voucherOrder) {
        // 在Redis已经做了库存是否充足和一人一单的校验,能够到这里说明用户已经秒杀成功了,所以这里其实不需要加锁
        // 1.扣减库存
        boolean success = seckillVoucherService.update()
                .setSql("stock = stock -1")
                .eq("voucher_id", voucherOrder.getId())
                .gt("stock", 0)
                .update();
        if(!success){
            // 扣减库存失败
            log.error("库存不足");
            return;
        }
        // 2.创建订单
        save(voucherOrder);
    }

    // 当前类初始化完毕就立马执行该方法
    @PostConstruct
    private void init() {
        // 执行线程任务
        SECKILL_ORDER_EXECUTOR.submit(new VoucherOrderHandler());
    }

    /**
     * 抢购秒杀券
     * @param voucherId
     * @return
     */
    @Override
    public Result seckillVoucher(Long voucherId) {
        Long userId = UserHolder.getUser().getId();

        // 1.执行lua脚本,判断是否有资格下单
        Long result = stringRedisTemplate.execute(
                SECKILL_SCRIPT,
                Collections.emptyList(),
                voucherId.toString(),
                userId.toString()
        );
        if(result == 1){
            return Result.fail("库存不足");
        }
        if(result == 2){
            return Result.fail("重复下单");
        }

        // 有购买资格
        long orderId = redisIdWorker.nextId("order"); // 分布式id
        // 2.保存信息到阻塞队列,会有一个线程不断从当中取出信息,执行扣库存和生成订单
        VoucherOrder voucherOrder = new VoucherOrder();
        voucherOrder.setId(orderId);    // 订单ID
        voucherOrder.setUserId(userId); // 用户ID
        voucherOrder.setVoucherId(voucherId); // 优惠券ID
        orderTasks.add(voucherOrder);
        return Result.ok(orderId);

    }

    @Transactional
    public Result createVoucherOrder(Long voucherId){
        // 4、一人一单校验
        Long userId = UserHolder.getUser().getId();

        int count = Math.toIntExact(query()
                .eq("user_id", userId)
                .eq("voucher_id", voucherId)
                .count());

        if(count > 0){
            // 用户已购买过该优惠券
            return Result.fail("用户已购买过该优惠券");
        }

        // 5、扣减库存
        // 使用CAS法增加乐观锁
        // 乐观锁：如果数据有改动，那么就失败
        boolean success = seckillVoucherService.update()
                .setSql("stock = stock -1")
                .eq("voucher_id", voucherId)
//                .eq("stock", voucher.getStock())
                .gt("stock", 0)
                .update();
        if (!success) {
            throw new RuntimeException("秒杀券扣减库存失败");
        }
        // 6、创建订单
        VoucherOrder voucherOrder = new VoucherOrder();
        // 分布式ID作为订单主键ID
        long orderId = redisIdWorker.nextId("order");
        voucherOrder.setId(orderId);
        voucherOrder.setUserId(userId);
        voucherOrder.setVoucherId(voucherId);
        save(voucherOrder);
        // 7、返回订单id
        return Result.ok(orderId);
    }
}
