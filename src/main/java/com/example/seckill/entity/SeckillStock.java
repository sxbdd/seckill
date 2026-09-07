package com.example.seckill.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_seckill_stock")
public class SeckillStock {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long activityId;
    private Integer stock;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
