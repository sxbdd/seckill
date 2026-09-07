package com.example.seckill.service;

import com.example.seckill.common.BusinessException;
import com.example.seckill.common.ResultCode;
import com.example.seckill.entity.SeckillActivity;
import com.example.seckill.mapper.SeckillActivityMapper;
import com.google.common.util.concurrent.RateLimiter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SeckillService {

    private final SeckillActivityMapper activityMapper;
    private final StringRedisTemplate redisTemplate;
    private final DefaultRedisScript<Long> seckillScript;
    private final OrderService orderService;
    private final ActivityService activityService;
    private final RateLimiter seckillRateLimiter;

    @Value("${seckill.redis-enabled:true}")
    private boolean redisEnabled;

    public String grab(Long activityId, Long userId) {
        if (!seckillRateLimiter.tryAcquire(1)) {
            throw new BusinessException(ResultCode.TOO_MANY_REQUESTS);
        }
        SeckillActivity activity = activityMapper.selectById(activityId);
        if (activity == null) {
            throw new BusinessException(ResultCode.ACTIVITY_NOT_EXIST);
        }
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(activity.getStartTime())) {
            throw new BusinessException(ResultCode.ACTIVITY_NOT_STARTED);
        }
        if (!now.isBefore(activity.getEndTime())) {
            throw new BusinessException(ResultCode.ACTIVITY_ENDED);
        }

        if (!redisEnabled) {
            return orderService.createSeckillOrder(activity, userId);
        }

        String buyKey = "seckill:buy:" + userId + ":" + activityId;
        Duration buyTtl = Duration.between(now, activity.getEndTime()).plusSeconds(60);
        if (buyTtl.isNegative()) {
            buyTtl = Duration.ofSeconds(60);
        }
        Boolean first = redisTemplate.opsForValue().setIfAbsent(buyKey, "1", buyTtl);
        if (Boolean.FALSE.equals(first)) {
            throw new BusinessException(ResultCode.REPEAT_PURCHASE);
        }

        String stockKey = "seckill:stock:" + activityId;
        Long result = redisTemplate.execute(seckillScript, List.of(stockKey));
        if (result == null) {
            result = -1L;
        }
        if (result == -1L) {
            activityService.reloadStock(activityId);
            result = redisTemplate.execute(seckillScript, List.of(stockKey));
            if (result == null) {
                result = -1L;
            }
        }
        if (result == -1L) {
            redisTemplate.delete(buyKey);
            throw new BusinessException(ResultCode.SYSTEM_ERROR, "库存键缺失");
        }
        if (result == 0L) {
            redisTemplate.delete(buyKey);
            throw new BusinessException(ResultCode.SOLD_OUT);
        }

        try {
            return orderService.createSeckillOrder(activity, userId);
        } catch (BusinessException e) {
            redisTemplate.opsForValue().increment(stockKey);
            redisTemplate.delete(buyKey);
            throw e;
        }
    }
}
