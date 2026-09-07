package com.example.seckill.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.seckill.entity.User;
import com.example.seckill.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class AdminInitializer implements ApplicationRunner {

    private final UserMapper userMapper;
    private final BCryptPasswordEncoder passwordEncoder;

    @Value("${seckill.admin-password:admin123}")
    private String adminPassword;

    @Override
    public void run(ApplicationArguments args) {
        Long count = userMapper.selectCount(new LambdaQueryWrapper<User>().eq(User::getUsername, "admin"));
        if (count == null || count == 0) {
            User admin = new User();
            admin.setUsername("admin");
            admin.setPassword(passwordEncoder.encode(adminPassword));
            admin.setNickname("管理员");
            admin.setRole("ADMIN");
            admin.setStatus(1);
            userMapper.insert(admin);
            log.info("已初始化管理员账号 admin");
        }
    }
}
