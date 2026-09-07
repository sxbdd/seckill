package com.example.seckill.dto;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record OrderVO(
        String orderNo,
        String goodsName,
        BigDecimal seckillPrice,
        Integer status,
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime createTime,
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime payTime,
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime cancelTime) {
}
