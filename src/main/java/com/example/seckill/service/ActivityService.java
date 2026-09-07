package com.example.seckill.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.seckill.common.BusinessException;
import com.example.seckill.common.ResultCode;
import com.example.seckill.dto.ActivityCreateRequest;
import com.example.seckill.dto.ActivityVO;
import com.example.seckill.dto.PageResult;
import com.example.seckill.entity.Goods;
import com.example.seckill.entity.SeckillActivity;
import com.example.seckill.entity.SeckillStock;
import com.example.seckill.mapper.GoodsMapper;
import com.example.seckill.mapper.SeckillActivityMapper;
import com.example.seckill.mapper.SeckillStockMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ActivityService {

    private final SeckillActivityMapper activityMapper;
    private final SeckillStockMapper stockMapper;
    private final GoodsMapper goodsMapper;
    private final StringRedisTemplate redisTemplate;

    @Value("${seckill.redis-enabled:true}")
    private boolean redisEnabled;

    @Transactional
    public Long create(ActivityCreateRequest dto) {
        Goods goods = goodsMapper.selectById(dto.goodsId());
        if (goods == null || goods.getStatus() == null || goods.getStatus() != 1) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "商品不存在或已下架");
        }
        LocalDateTime now = LocalDateTime.now();
        if (!dto.endTime().isAfter(dto.startTime())) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "结束时间必须晚于开始时间");
        }
        if (dto.startTime().isBefore(now.minusSeconds(60))) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "开始时间不能早于当前时间");
        }
        SeckillActivity activity = new SeckillActivity();
        activity.setGoodsId(dto.goodsId());
        activity.setSeckillPrice(dto.seckillPrice());
        activity.setStartTime(dto.startTime());
        activity.setEndTime(dto.endTime());
        activity.setStatus(0);
        activityMapper.insert(activity);

        SeckillStock stock = new SeckillStock();
        stock.setActivityId(activity.getId());
        stock.setStock(dto.stock());
        stockMapper.insert(stock);

        preheat(activity, dto.stock());
        return activity.getId();
    }

    public PageResult<ActivityVO> listUser(long page, long size) {
        Page<SeckillActivity> p = new Page<>(page, size);
        LambdaQueryWrapper<SeckillActivity> w = new LambdaQueryWrapper<>();
        w.gt(SeckillActivity::getEndTime, LocalDateTime.now())
                .orderByAsc(SeckillActivity::getStartTime);
        activityMapper.selectPage(p, w);
        List<ActivityVO> list = p.getRecords().stream().map(a -> toUserVO(a, true)).toList();
        return new PageResult<>(p.getTotal(), list);
    }

    public ActivityVO detailUser(Long id) {
        SeckillActivity activity = activityMapper.selectById(id);
        if (activity == null) {
            throw new BusinessException(ResultCode.ACTIVITY_NOT_EXIST);
        }
        return toUserVO(activity, true);
    }

    public PageResult<ActivityVO> listAdmin(long page, long size) {
        Page<SeckillActivity> p = new Page<>(page, size);
        activityMapper.selectPage(p, new LambdaQueryWrapper<SeckillActivity>().orderByDesc(SeckillActivity::getId));
        List<ActivityVO> list = p.getRecords().stream().map(a -> toUserVO(a, false)).toList();
        return new PageResult<>(p.getTotal(), list);
    }

    public ActivityVO detailAdmin(Long id) {
        SeckillActivity activity = activityMapper.selectById(id);
        if (activity == null) {
            throw new BusinessException(ResultCode.ACTIVITY_NOT_EXIST);
        }
        return toUserVO(activity, false);
    }

    public Map<String, Integer> reload(Long activityId) {
        SeckillActivity activity = activityMapper.selectById(activityId);
        if (activity == null) {
            throw new BusinessException(ResultCode.ACTIVITY_NOT_EXIST);
        }
        int dbStock = dbStock(activityId);
        if (redisEnabled) {
            preheat(activity, dbStock);
        }
        String redisStock = redisEnabled ? redisTemplate.opsForValue().get("seckill:stock:" + activityId) : null;
        return Map.of("dbStock", dbStock, "redisStock", redisStock == null ? -1 : Integer.parseInt(redisStock));
    }

    public void reloadStock(Long activityId) {
        int dbStock = dbStock(activityId);
        redisTemplate.opsForValue().set("seckill:stock:" + activityId, String.valueOf(dbStock), ttlUntilEnd(activityId));
    }

    private void preheat(SeckillActivity activity, int stock) {
        if (!redisEnabled) {
            return;
        }
        try {
            String stockKey = "seckill:stock:" + activity.getId();
            Duration ttl = ttlUntilEnd(activity.getId());
            redisTemplate.opsForValue().set(stockKey, String.valueOf(stock), ttl);
            String infoKey = "seckill:info:" + activity.getId();
            redisTemplate.opsForHash().put(infoKey, "endTime",
                    String.valueOf(activity.getEndTime().atZone(ZoneId.systemDefault()).toEpochSecond()));
            redisTemplate.expire(infoKey, ttl);
        } catch (Exception e) {
            redisTemplate.delete("seckill:stock:" + activity.getId());
            redisTemplate.delete("seckill:info:" + activity.getId());
            throw new BusinessException(ResultCode.SYSTEM_ERROR, "Redis 预热失败");
        }
    }

    private int dbStock(Long activityId) {
        SeckillStock stock = stockMapper.selectOne(new LambdaQueryWrapper<SeckillStock>().eq(SeckillStock::getActivityId, activityId));
        return stock == null ? 0 : stock.getStock();
    }

    private Duration ttlUntilEnd(Long activityId) {
        SeckillActivity activity = activityMapper.selectById(activityId);
        if (activity == null) {
            return Duration.ofSeconds(60);
        }
        Duration ttl = Duration.between(LocalDateTime.now(), activity.getEndTime()).plusSeconds(60);
        return ttl.isNegative() ? Duration.ofSeconds(60) : ttl;
    }

    private ActivityVO toUserVO(SeckillActivity a, boolean withStock) {
        LocalDateTime now = LocalDateTime.now();
        int status = now.isBefore(a.getStartTime()) ? 0 : (now.isBefore(a.getEndTime()) ? 1 : 2);
        long leftSeconds = now.isBefore(a.getStartTime()) ? Duration.between(now, a.getStartTime()).getSeconds() : 0;
        boolean soldOut = soldOut(a.getId());
        Integer stock = null;
        if (!withStock) {
            stock = dbStock(a.getId());
        }
        Goods goods = goodsMapper.selectById(a.getGoodsId());
        String goodsName = goods == null ? "商品" + a.getGoodsId() : goods.getGoodsName();
        return new ActivityVO(a.getId(), a.getGoodsId(), goodsName, a.getSeckillPrice(),
                a.getStartTime(), a.getEndTime(), status, leftSeconds, soldOut, stock);
    }

    private boolean soldOut(Long activityId) {
        if (redisEnabled) {
            String v = redisTemplate.opsForValue().get("seckill:stock:" + activityId);
            if (v != null) {
                return Integer.parseInt(v) <= 0;
            }
        }
        return dbStock(activityId) <= 0;
    }
}
