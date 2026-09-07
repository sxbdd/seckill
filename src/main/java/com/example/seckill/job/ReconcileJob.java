package com.example.seckill.job;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.seckill.entity.SeckillActivity;
import com.example.seckill.entity.SeckillStock;
import com.example.seckill.mapper.SeckillActivityMapper;
import com.example.seckill.mapper.SeckillStockMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReconcileJob {

    private final SeckillActivityMapper activityMapper;
    private final SeckillStockMapper stockMapper;
    private final StringRedisTemplate redisTemplate;

    @Value("${seckill.redis-enabled:true}")
    private boolean redisEnabled;

    @Value("${seckill.reconcile-enabled:true}")
    private boolean reconcileEnabled;

    @Scheduled(fixedDelayString = "${seckill.reconcile-interval-ms:60000}")
    public void reconcile() {
        if (!reconcileEnabled || !redisEnabled) {
            return;
        }
        List<SeckillActivity> activities = activityMapper.selectList(
                new LambdaQueryWrapper<SeckillActivity>().gt(SeckillActivity::getEndTime, LocalDateTime.now()));
        for (SeckillActivity activity : activities) {
            SeckillStock stock = stockMapper.selectOne(
                    new LambdaQueryWrapper<SeckillStock>().eq(SeckillStock::getActivityId, activity.getId()));
            if (stock == null) {
                continue;
            }
            String key = "seckill:stock:" + activity.getId();
            String redisStock = redisTemplate.opsForValue().get(key);
            int dbStock = stock.getStock();
            if (redisStock == null || Integer.parseInt(redisStock) != dbStock) {
                Duration ttl = Duration.between(LocalDateTime.now(), activity.getEndTime()).plusSeconds(60);
                if (ttl.isNegative()) {
                    ttl = Duration.ofSeconds(60);
                }
                redisTemplate.opsForValue().set(key, String.valueOf(dbStock), ttl);
                log.warn("对账修复 activityId={} redisStock={} dbStock={}", activity.getId(), redisStock, dbStock);
            }
        }
    }
}
