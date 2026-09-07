package com.example.seckill.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.seckill.common.BusinessException;
import com.example.seckill.common.ResultCode;
import com.example.seckill.dto.OrderVO;
import com.example.seckill.dto.PageResult;
import com.example.seckill.entity.Goods;
import com.example.seckill.entity.SeckillActivity;
import com.example.seckill.entity.SeckillOrder;
import com.example.seckill.mapper.GoodsMapper;
import com.example.seckill.mapper.SeckillOrderMapper;
import com.example.seckill.mapper.SeckillStockMapper;
import com.example.seckill.utils.OrderNoGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final SeckillOrderMapper orderMapper;
    private final SeckillStockMapper stockMapper;
    private final GoodsMapper goodsMapper;
    private final StringRedisTemplate redisTemplate;

    @Value("${seckill.redis-enabled:true}")
    private boolean redisEnabled;

    @Transactional
    public String createSeckillOrder(SeckillActivity activity, Long userId) {
        int rows = stockMapper.reduceStock(activity.getId());
        if (rows == 0) {
            throw new BusinessException(ResultCode.SOLD_OUT);
        }
        Goods goods = goodsMapper.selectById(activity.getGoodsId());
        SeckillOrder order = new SeckillOrder();
        order.setOrderNo(OrderNoGenerator.next());
        order.setUserId(userId);
        order.setActivityId(activity.getId());
        order.setGoodsId(activity.getGoodsId());
        order.setGoodsName(goods == null ? "商品" + activity.getGoodsId() : goods.getGoodsName());
        order.setSeckillPrice(activity.getSeckillPrice());
        order.setStatus(0);
        try {
            orderMapper.insert(order);
        } catch (DuplicateKeyException e) {
            throw new BusinessException(ResultCode.REPEAT_PURCHASE);
        }
        return order.getOrderNo();
    }

    public PageResult<OrderVO> mine(long page, long size, Long userId) {
        Page<SeckillOrder> p = new Page<>(page, size);
        LambdaQueryWrapper<SeckillOrder> w = new LambdaQueryWrapper<SeckillOrder>()
                .eq(SeckillOrder::getUserId, userId)
                .orderByDesc(SeckillOrder::getId);
        orderMapper.selectPage(p, w);
        List<OrderVO> list = p.getRecords().stream().map(this::toVO).toList();
        return new PageResult<>(p.getTotal(), list);
    }

    public OrderVO detail(String orderNo, Long userId) {
        return toVO(findOwn(orderNo, userId));
    }

    @Transactional
    public OrderVO cancel(String orderNo, Long userId) {
        SeckillOrder order = findOwn(orderNo, userId);
        if (order.getStatus() == 2) {
            return toVO(order);
        }
        if (order.getStatus() != 0) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "仅待支付订单可取消");
        }
        LocalDateTime now = LocalDateTime.now();
        orderMapper.update(null, new LambdaUpdateWrapper<SeckillOrder>()
                .eq(SeckillOrder::getId, order.getId())
                .eq(SeckillOrder::getStatus, 0)
                .set(SeckillOrder::getStatus, 2)
                .set(SeckillOrder::getCancelTime, now));
        stockMapper.increaseStock(order.getActivityId());
        registerAfterCommit(() -> {
            if (redisEnabled) {
                redisTemplate.opsForValue().increment("seckill:stock:" + order.getActivityId());
                redisTemplate.delete("seckill:buy:" + userId + ":" + order.getActivityId());
            }
        });
        order.setStatus(2);
        order.setCancelTime(now);
        return toVO(order);
    }

    @Transactional
    public OrderVO pay(String orderNo, Long userId) {
        SeckillOrder order = findOwn(orderNo, userId);
        if (order.getStatus() == 1) {
            return toVO(order);
        }
        if (order.getStatus() != 0) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "仅待支付订单可支付");
        }
        LocalDateTime now = LocalDateTime.now();
        orderMapper.update(null, new LambdaUpdateWrapper<SeckillOrder>()
                .eq(SeckillOrder::getId, order.getId())
                .eq(SeckillOrder::getStatus, 0)
                .set(SeckillOrder::getStatus, 1)
                .set(SeckillOrder::getPayTime, now));
        order.setStatus(1);
        order.setPayTime(now);
        return toVO(order);
    }

    private SeckillOrder findOwn(String orderNo, Long userId) {
        SeckillOrder order = orderMapper.selectOne(new LambdaQueryWrapper<SeckillOrder>().eq(SeckillOrder::getOrderNo, orderNo));
        if (order == null) {
            throw new BusinessException(ResultCode.NOT_FOUND);
        }
        if (!order.getUserId().equals(userId)) {
            throw new BusinessException(ResultCode.FORBIDDEN);
        }
        return order;
    }

    private OrderVO toVO(SeckillOrder o) {
        return new OrderVO(o.getOrderNo(), o.getGoodsName(), o.getSeckillPrice(), o.getStatus(),
                o.getCreateTime(), o.getPayTime(), o.getCancelTime());
    }

    private void registerAfterCommit(Runnable action) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                action.run();
            }
        });
    }
}
