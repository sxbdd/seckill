package com.example.seckill;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.seckill.entity.Goods;
import com.example.seckill.entity.SeckillOrder;
import com.example.seckill.entity.SeckillStock;
import com.example.seckill.entity.User;
import com.example.seckill.mapper.GoodsMapper;
import com.example.seckill.mapper.SeckillOrderMapper;
import com.example.seckill.mapper.SeckillStockMapper;
import com.example.seckill.mapper.UserMapper;
import com.fasterxml.jackson.databind.JsonNode;
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
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SeckillIntegrationTests {

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
        goods.setGoodsName("iPhone 15");
        goods.setGoodsDesc("测试商品");
        goods.setPrice(new BigDecimal("5999.00"));
        goods.setStock(1000);
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

    // ---------- helpers ----------
    private String adminToken() throws Exception {
        return login("admin", "admin123");
    }

    private String register(String username) throws Exception {
        String body = om.writeValueAsString(Map.of(
                "username", username, "password", "12345678", "nickname", username));
        MvcResult r = mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn();
        return om.readTree(r.getResponse().getContentAsString()).path("data").path("token").asText();
    }

    private String login(String username, String password) throws Exception {
        String body = om.writeValueAsString(Map.of("username", username, "password", password));
        MvcResult r = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andReturn();
        return om.readTree(r.getResponse().getContentAsString()).path("data").path("token").asText();
    }

    private long createActivity(String token, int stock, long startOffsetSeconds) throws Exception {
        LocalDateTime start = LocalDateTime.now().plusSeconds(startOffsetSeconds);
        LocalDateTime end = start.plusHours(1);
        String body = om.writeValueAsString(Map.of(
                "goodsId", goodsId,
                "seckillPrice", 100.00,
                "startTime", start.format(FMT),
                "endTime", end.format(FMT),
                "stock", stock));
        MvcResult r = mvc.perform(post("/api/admin/activities")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn();
        return om.readTree(r.getResponse().getContentAsString()).path("data").path("activityId").asLong();
    }

    private MvcResult seckill(String token, long activityId) throws Exception {
        return mvc.perform(post("/api/seckill/" + activityId + "/orders")
                        .header("Authorization", "Bearer " + token))
                .andReturn();
    }

    private int code(MvcResult r) throws Exception {
        JsonNode n = om.readTree(r.getResponse().getContentAsString());
        return n.path("code").asInt();
    }

    private String orderNo(MvcResult r) throws Exception {
        JsonNode n = om.readTree(r.getResponse().getContentAsString());
        return n.path("data").path("orderNo").asText();
    }

    // ---------- tests ----------
    @Test
    void register_then_duplicate_rejected() throws Exception {
        String token = register("alice");
        assertNotNull(token);
        String body = om.writeValueAsString(Map.of("username", "alice", "password", "12345678"));
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void unauthorized_seckill_rejected() throws Exception {
        mvc.perform(post("/api/seckill/1/orders"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    void full_seckill_and_duplicate() throws Exception {
        String user = register("user1");
        long aid = createActivity(adminToken(), 1, -5);
        MvcResult first = seckill(user, aid);
        assertEquals(200, code(first));
        assertNotNull(orderNo(first));

        MvcResult dup = seckill(user, aid);
        assertEquals(2002, code(dup));

        SeckillStock stock = stockMapper.selectOne(new LambdaQueryWrapper<SeckillStock>().eq(SeckillStock::getActivityId, aid));
        assertEquals(0, stock.getStock());
        Long orderCount = orderMapper.selectCount(new LambdaQueryWrapper<SeckillOrder>().eq(SeckillOrder::getActivityId, aid));
        assertEquals(1L, orderCount);
    }

    @Test
    void sold_out_for_second_user() throws Exception {
        String u1 = register("user1");
        String u2 = register("user2");
        long aid = createActivity(adminToken(), 1, -5);
        assertEquals(200, code(seckill(u1, aid)));
        assertEquals(2001, code(seckill(u2, aid)));
    }

    @Test
    void cancel_then_regrab() throws Exception {
        String user = register("user1");
        long aid = createActivity(adminToken(), 2, -5);
        MvcResult first = seckill(user, aid);
        assertEquals(200, code(first));
        String no = orderNo(first);

        mvc.perform(post("/api/orders/" + no + "/cancel")
                        .header("Authorization", "Bearer " + user))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.status").value(2));

        SeckillStock stock = stockMapper.selectOne(new LambdaQueryWrapper<SeckillStock>().eq(SeckillStock::getActivityId, aid));
        assertEquals(2, stock.getStock());
        assertEquals("2", redisTemplate.opsForValue().get("seckill:stock:" + aid));

        MvcResult second = seckill(user, aid);
        assertEquals(200, code(second));

        Long activeCount = orderMapper.selectCount(new LambdaQueryWrapper<SeckillOrder>()
                .eq(SeckillOrder::getUserId, userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getUsername, "user1")).getId())
                .eq(SeckillOrder::getActivityId, aid)
                .in(SeckillOrder::getStatus, 0, 1));
        assertEquals(1L, activeCount);
    }

    @Test
    void pay_rules_and_cancel_of_paid_rejected() throws Exception {
        String user = register("user1");
        long aid = createActivity(adminToken(), 1, -5);
        String no = orderNo(seckill(user, aid));

        mvc.perform(post("/api/orders/" + no + "/pay").header("Authorization", "Bearer " + user))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value(1));
        // 幂等
        mvc.perform(post("/api/orders/" + no + "/pay").header("Authorization", "Bearer " + user))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value(1));
        // 已支付不可取消
        mvc.perform(post("/api/orders/" + no + "/cancel").header("Authorization", "Bearer " + user))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void cancel_idempotent() throws Exception {
        String user = register("user1");
        long aid = createActivity(adminToken(), 1, -5);
        String no = orderNo(seckill(user, aid));
        mvc.perform(post("/api/orders/" + no + "/cancel").header("Authorization", "Bearer " + user))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value(2));
        mvc.perform(post("/api/orders/" + no + "/cancel").header("Authorization", "Bearer " + user))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value(2));
    }

    @Test
    void cannot_view_others_order() throws Exception {
        String u1 = register("user1");
        String u2 = register("user2");
        long aid = createActivity(adminToken(), 1, -5);
        String no = orderNo(seckill(u1, aid));
        mvc.perform(get("/api/orders/" + no).header("Authorization", "Bearer " + u2))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    void activity_not_started() throws Exception {
        String user = register("user1");
        long aid = createActivity(adminToken(), 1, 600);
        assertEquals(1001, code(seckill(user, aid)));
    }

    @Test
    void activity_not_exist() throws Exception {
        String user = register("user1");
        assertEquals(1003, code(seckill(user, 99999L)));
    }

    @Test
    void non_admin_cannot_create_activity() throws Exception {
        String user = register("user1");
        LocalDateTime start = LocalDateTime.now().plusMinutes(10);
        String body = om.writeValueAsString(Map.of(
                "goodsId", goodsId, "seckillPrice", 100.00,
                "startTime", start.format(FMT), "endTime", start.plusHours(1).format(FMT), "stock", 10));
        mvc.perform(post("/api/admin/activities").header("Authorization", "Bearer " + user)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    void goods_not_found() throws Exception {
        mvc.perform(get("/api/goods/99999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404));
    }

    @Test
    void activity_list_and_detail_public() throws Exception {
        long aid = createActivity(adminToken(), 5, -5);
        mvc.perform(get("/api/seckill/activities"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        mvc.perform(get("/api/seckill/activities/" + aid))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(aid));
    }
}

