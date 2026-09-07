package com.example.seckill.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("t_seckill_order")
public class SeckillOrder {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String orderNo;
    private Long userId;
    private Long activityId;
    private Long goodsId;
    private String goodsName;
    private BigDecimal seckillPrice;
    private Integer status;
    @TableField(insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private String activeKey;
    private LocalDateTime createTime;
    private LocalDateTime payTime;
    private LocalDateTime cancelTime;
}
