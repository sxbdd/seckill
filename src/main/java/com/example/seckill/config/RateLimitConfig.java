package com.example.seckill.config;

import com.google.common.util.concurrent.RateLimiter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RateLimitConfig {

    @Bean
    public RateLimiter seckillRateLimiter(@Value("${seckill.rate-limit-qps:2000}") double qps) {
        return RateLimiter.create(qps);
    }
}
