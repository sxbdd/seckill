package com.example.seckill.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.seckill.common.BusinessException;
import com.example.seckill.common.ResultCode;
import com.example.seckill.dto.LoginRequest;
import com.example.seckill.dto.LoginResponse;
import com.example.seckill.dto.RegisterRequest;
import com.example.seckill.entity.User;
import com.example.seckill.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserMapper userMapper;
    private final BCryptPasswordEncoder passwordEncoder;
    private final StringRedisTemplate redisTemplate;

    @Value("${seckill.token-ttl:2h}")
    private Duration tokenTtl;

    public LoginResponse register(RegisterRequest req) {
        Long count = userMapper.selectCount(new LambdaQueryWrapper<User>().eq(User::getUsername, req.username()));
        if (count != null && count > 0) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "用户名已存在");
        }
        User user = new User();
        user.setUsername(req.username());
        user.setPassword(passwordEncoder.encode(req.password()));
        user.setNickname(req.nickname());
        user.setRole("USER");
        user.setStatus(1);
        userMapper.insert(user);
        return login(new LoginRequest(req.username(), req.password()));
    }

    public LoginResponse login(LoginRequest req) {
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getUsername, req.username()));
        if (user == null || !passwordEncoder.matches(req.password(), user.getPassword())) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "用户名或密码错误");
        }
        String token = UUID.randomUUID().toString().replace("-", "");
        redisTemplate.opsForValue().set("user:token:" + token, user.getId() + "|" + user.getRole(), tokenTtl);
        return new LoginResponse(token, user.getId(), user.getNickname(), user.getRole());
    }

    public void logout(String token) {
        redisTemplate.delete("user:token:" + token);
    }
}
