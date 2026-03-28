package com.blog.task;

import com.blog.service.IOrderService;
import com.blog.service.IVoucherService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 订单超时自动关闭
 */
@Component
@Slf4j
public class OrderAutoCloseTask {
    // 订单超时时间(分钟)
    private static final int ORDER_EXPIRE_MINUTES = 15;

    @Resource
    private IOrderService orderService;

    @Scheduled(cron = "0 */1 * * * ?") // 每分钟执行一次
    public void autoCloseExpiredOrders() {
        log.info("开始执行未支付订单关闭任务...");
        try {
            orderService.closeExpiredOrders(ORDER_EXPIRE_MINUTES);
        } catch (Exception e) {
            log.error("订单关闭任务执行异常", e);
        }
    }
}
