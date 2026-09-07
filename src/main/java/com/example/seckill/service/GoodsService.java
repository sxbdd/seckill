package com.example.seckill.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.seckill.common.BusinessException;
import com.example.seckill.common.ResultCode;
import com.example.seckill.dto.GoodsVO;
import com.example.seckill.dto.PageResult;
import com.example.seckill.entity.Goods;
import com.example.seckill.mapper.GoodsMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@Slf4j
@Service
@RequiredArgsConstructor
public class GoodsService {

    private static final String NULL_MARK = "__NULL__";

    private final GoodsMapper goodsMapper;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public PageResult<GoodsVO> list(long page, long size) {
        Page<Goods> p = new Page<>(page, size);
        goodsMapper.selectPage(p, new LambdaQueryWrapper<Goods>().eq(Goods::getStatus, 1).orderByAsc(Goods::getId));
        List<GoodsVO> list = p.getRecords().stream().map(this::toVO).toList();
        return new PageResult<>(p.getTotal(), list);
    }

    public GoodsVO getById(Long id) {
        String cacheKey = "goods:info:" + id;
        String cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            if (NULL_MARK.equals(cached)) {
                throw new BusinessException(ResultCode.NOT_FOUND);
            }
            return toVO(readGoods(cached));
        }

        String lockKey = "lock:goods:reload:" + id;
        Boolean locked = redisTemplate.opsForValue().setIfAbsent(lockKey, "1", Duration.ofSeconds(3));
        if (Boolean.TRUE.equals(locked)) {
            try {
                Goods goods = loadAndCache(id, cacheKey);
                return toVO(goods);
            } finally {
                redisTemplate.delete(lockKey);
            }
        }

        try {
            Thread.sleep(50);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        String retry = redisTemplate.opsForValue().get(cacheKey);
        if (retry != null) {
            if (NULL_MARK.equals(retry)) {
                throw new BusinessException(ResultCode.NOT_FOUND);
            }
            return toVO(readGoods(retry));
        }
        return toVO(loadAndCache(id, cacheKey));
    }

    private Goods loadAndCache(Long id, String cacheKey) {
        Goods goods = goodsMapper.selectById(id);
        if (goods == null || goods.getStatus() == null || goods.getStatus() != 1) {
            redisTemplate.opsForValue().set(cacheKey, NULL_MARK, Duration.ofSeconds(60));
            throw new BusinessException(ResultCode.NOT_FOUND);
        }
        int ttlSeconds = 300 + ThreadLocalRandom.current().nextInt(300);
        redisTemplate.opsForValue().set(cacheKey, writeGoods(goods), Duration.ofSeconds(ttlSeconds));
        return goods;
    }

    private Goods readGoods(String json) {
        try {
            return objectMapper.readValue(json, Goods.class);
        } catch (Exception e) {
            throw new IllegalStateException("缓存反序列化失败", e);
        }
    }

    private String writeGoods(Goods goods) {
        try {
            return objectMapper.writeValueAsString(goods);
        } catch (Exception e) {
            throw new IllegalStateException("缓存序列化失败", e);
        }
    }

    private GoodsVO toVO(Goods g) {
        return new GoodsVO(g.getId(), g.getGoodsName(), g.getGoodsDesc(), g.getPrice(), g.getStatus());
    }
}
