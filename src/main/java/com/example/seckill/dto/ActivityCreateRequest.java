package com.example.seckill.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ActivityCreateRequest(
        @NotNull(message = "商品不能为空") Long goodsId,

        @NotNull(message = "秒杀价不能为空")
        @DecimalMin(value = "0.01", message = "秒杀价必须大于0")
        BigDecimal seckillPrice,

        @NotNull(message = "开始时间不能为空")
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        LocalDateTime startTime,

        @NotNull(message = "结束时间不能为空")
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        LocalDateTime endTime,

        @NotNull(message = "库存不能为空")
        @Min(value = 1, message = "库存至少1")
        @Max(value = 100000, message = "库存最多100000")
        Integer stock) {
}
