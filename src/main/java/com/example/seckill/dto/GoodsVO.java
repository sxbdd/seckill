package com.example.seckill.dto;

import java.math.BigDecimal;

public record GoodsVO(Long id, String goodsName, String goodsDesc, BigDecimal price, Integer status) {
}
