package com.example.seckill.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("t_goods")
public class Goods {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String goodsName;
    private String goodsDesc;
    private BigDecimal price;
    private Integer stock;
    private Integer status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
