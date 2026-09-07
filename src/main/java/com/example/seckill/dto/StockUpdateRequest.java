package com.example.seckill.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record StockUpdateRequest(
        @NotNull(message = "库存不能为空")
        @Min(value = 0, message = "库存不能为负")
        @Max(value = 100000, message = "库存最多100000")
        Integer stock) {
}
