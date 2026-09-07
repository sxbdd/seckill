package com.example.seckill;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.seckill.entity.Goods;
import com.example.seckill.entity.SeckillStock;
import com.example.seckill.entity.User;
import com.example.seckill.mapper.GoodsMapper;
import com.example.seckill.mapper.SeckillOrderMapper;
import com.example.seckill.mapper.SeckillStockMapper;
import com.example.seckill.mapper.UserMapper;
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
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = "seckill.redis-enabled=false")
class DegradedSeckillTest {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;
    @Autowired UserMapper userMapper;
    @Autowired GoodsMapper goodsMapper;
    @Autowired SeckillStockMapper stockMapper;
    @Autowired SeckillOrderMapper orderMapper;
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
        goods.setGoodsName("机械键盘");
        goods.setGoodsDesc("降级测试商品");
        goods.setPrice(new BigDecimal("399.00"));
        goods.setStock(500);
        goods.setStatus(1);
        goodsMapper.insert(goods);
        goodsId = goods.getId();

        User admin = new User();
        admin.setUsername("admin");
        admin.setPassword(encoder.encode("admin123"));
        admin.setNickname("管理员");
        admin.setRole("ADMIN");
        admin.setStatus(1);
        userMapper.insert(admin);
    }

    @Test
    void degraded_db_only_seckill_works() throws Exception {
        // register user
        String reg = om.writeValueAsString(Map.of("username", "user1", "password", "12345678"));
        MvcResult rr = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(reg))
                .andExpect(status().isOk()).andReturn();
        String token = om.readTree(rr.getResponse().getContentAsString()).path("data").path("token").asText();

        // admin login
        String login = om.writeValueAsString(Map.of("username", "admin", "password", "admin123"));
        MvcResult lr = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(login))
                .andExpect(status().isOk()).andReturn();
        String adminToken = om.readTree(lr.getResponse().getContentAsString()).path("data").path("token").asText();

        LocalDateTime start = LocalDateTime.now().minusSeconds(5);
        String body = om.writeValueAsString(Map.of("goodsId", goodsId, "seckillPrice", 100.00,
                "startTime", start.format(FMT), "endTime", start.plusHours(1).format(FMT), "stock", 10));
        MvcResult cr = mvc.perform(post("/api/admin/activities").header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andReturn();
        long aid = om.readTree(cr.getResponse().getContentAsString()).path("data").path("activityId").asLong();

        MvcResult sr = mvc.perform(post("/api/seckill/" + aid + "/orders").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200)).andReturn();
        String orderNo = om.readTree(sr.getResponse().getContentAsString()).path("data").path("orderNo").asText();
        assertEquals(false, orderNo.isBlank());

        SeckillStock stock = stockMapper.selectOne(new LambdaQueryWrapper<SeckillStock>().eq(SeckillStock::getActivityId, aid));
        assertEquals(9, stock.getStock());
        assertEquals(null, redisTemplate.opsForValue().get("seckill:stock:" + aid));
    }
}

