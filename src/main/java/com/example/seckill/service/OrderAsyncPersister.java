package com.example.seckill.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.seckill.common.BusinessException;
import com.example.seckill.dto.PendingOrder;
import com.example.seckill.entity.SeckillActivity;
import com.example.seckill.mapper.SeckillActivityMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderAsyncPersister {

    private final OrderService orderService;
    private final SeckillActivityMapper activityMapper;
    private final StringRedisTemplate redisTemplate;

    private final LinkedBlockingQueue<PendingOrder> queue = new LinkedBlockingQueue<>(100000);
    private volatile boolean running = true;
    private Thread worker;

    @PostConstruct
    public void start() {
        worker = new Thread(this::run, "order-persist-worker");
        worker.setDaemon(true);
        worker.start();
    }

    @PreDestroy
    public void stop() {
        running = false;
        if (worker != null) {
            worker.interrupt();
        }
    }

    public boolean enqueue(PendingOrder order) {
        try {
            return queue.offer(order, 500, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    public int pendingCount() {
        return queue.size();
    }

    private void run() {
        while (running) {
            PendingOrder item;
            try {
                item = queue.poll(500, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                break;
            }
            if (item == null) {
                continue;
            }
            process(item);
        }
        PendingOrder remain;
        while ((remain = queue.poll()) != null) {
            process(remain);
        }
    }

    private void process(PendingOrder item) {
        try {
            SeckillActivity activity = activityMapper.selectById(item.activityId());
            if (activity == null) {
                compensate(item);
                log.warn("异步落库失败：活动不存在 activityId={}", item.activityId());
                return;
            }
            orderService.createSeckillOrder(activity, item.userId(), item.orderNo());
        } catch (BusinessException e) {
            compensate(item);
            log.warn("异步落库业务失败 activityId={} userId={} code={}", item.activityId(), item.userId(), e.getResultCode().getCode());
        } catch (Exception e) {
            compensate(item);
            log.error("异步落库异常 activityId={} userId={}", item.activityId(), item.userId(), e);
        }
    }

    private void compensate(PendingOrder item) {
        redisTemplate.opsForValue().increment("seckill:stock:" + item.activityId());
        redisTemplate.delete("seckill:buy:" + item.userId() + ":" + item.activityId());
    }
}
