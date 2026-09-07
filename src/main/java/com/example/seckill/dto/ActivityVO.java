package com.example.seckill.dto;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ActivityVO(
        Long id,
        Long goodsId,
        String goodsName,
        BigDecimal seckillPrice,
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime,
        Integer status,
        Long leftSeconds,
        Boolean soldOut,
        Integer stock) {
}
