package com.example.seckill;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.seckill.common.BusinessException;
import com.example.seckill.entity.Goods;
import com.example.seckill.entity.SeckillOrder;
import com.example.seckill.entity.SeckillStock;
import com.example.seckill.entity.User;
import com.example.seckill.mapper.GoodsMapper;
import com.example.seckill.mapper.SeckillOrderMapper;
import com.example.seckill.mapper.SeckillStockMapper;
import com.example.seckill.mapper.UserMapper;
import com.example.seckill.service.SeckillService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ConcurrentNoOversellTest {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;
    @Autowired UserMapper userMapper;
    @Autowired GoodsMapper goodsMapper;
    @Autowired SeckillStockMapper stockMapper;
    @Autowired SeckillOrderMapper orderMapper;
    @Autowired SeckillService seckillService;
    @Autowired StringRedisTemplate redisTemplate;
    @Autowired BCryptPasswordEncoder encoder;

    private Long goodsId;

    @BeforeEach
    void setUp() {
        orderMapper.delete(null);
        stockMapper.delete(null);
        goodsMapper.delete(null);
        userMapper.delete(null);
        var conn = redisTemplate.getConnectionFactory().getConnection();
        try {
            conn.serverCommands().flushDb();
        } finally {
            conn.close();
        }
        Goods goods = new Goods();
        goods.setGoodsName("并发测试商品");
        goods.setPrice(new BigDecimal("100.00"));
        goods.setStock(1000);
        goods.setStatus(1);
        goodsMapper.insert(goods);
        goodsId = goods.getId();

        User admin = new User();
        admin.setUsername("admin");
        admin.setPassword(encoder.encode("admin123"));
        admin.setRole("ADMIN");
        admin.setStatus(1);
        userMapper.insert(admin);
    }

    @Test
    void concurrent_150_threads_no_oversell() throws Exception {
        String login = om.writeValueAsString(Map.of("username", "admin", "password", "admin123"));
        MvcResult lr = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(login))
                .andExpect(status().isOk()).andReturn();
        String adminToken = om.readTree(lr.getResponse().getContentAsString()).path("data").path("token").asText();

        LocalDateTime start = LocalDateTime.now().minusSeconds(5);
        String body = om.writeValueAsString(Map.of("goodsId", goodsId, "seckillPrice", 100.00,
                "startTime", start.format(FMT), "endTime", start.plusHours(1).format(FMT), "stock", 50));
        MvcResult cr = mvc.perform(post("/api/admin/activities").header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200)).andReturn();
        long aid = om.readTree(cr.getResponse().getContentAsString()).path("data").path("activityId").asLong();

        List<Long> userIds = new ArrayList<>();
        for (int i = 0; i < 150; i++) {
            User u = new User();
            u.setUsername("cuser" + i);
            u.setPassword("x");
            u.setRole("USER");
            u.setStatus(1);
            userMapper.insert(u);
            userIds.add(u.getId());
        }

        ExecutorService pool = Executors.newFixedThreadPool(150);
        AtomicInteger success = new AtomicInteger();
        List<Future<?>> futures = new ArrayList<>();
        for (Long uid : userIds) {
            futures.add(pool.submit(() -> {
                try {
                    seckillService.grab(aid, uid);
                    success.incrementAndGet();
                } catch (BusinessException ignored) {
                    // 已抢完/重复等业务失败属预期
                } catch (Exception ignored) {
                }
            }));
        }
        for (Future<?> f : futures) {
            f.get(120, TimeUnit.SECONDS);
        }
        pool.shutdown();

        assertEquals(50, success.get(), "并发下成功订单数应恰等于库存");
        SeckillStock stock = stockMapper.selectOne(new LambdaQueryWrapper<SeckillStock>().eq(SeckillStock::getActivityId, aid));
        assertEquals(0, stock.getStock(), "库存应精确归零");
        Long orders = orderMapper.selectCount(new LambdaQueryWrapper<SeckillOrder>().eq(SeckillOrder::getActivityId, aid));
        assertEquals(50L, orders, "订单数应恰为 50");
    }
}
