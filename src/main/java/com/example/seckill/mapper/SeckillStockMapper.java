package com.example.seckill.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.seckill.entity.SeckillStock;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface SeckillStockMapper extends BaseMapper<SeckillStock> {

    @Update("UPDATE t_seckill_stock SET stock = stock - 1 WHERE activity_id = #{activityId} AND stock > 0")
    int reduceStock(@Param("activityId") Long activityId);

    @Update("UPDATE t_seckill_stock SET stock = stock + 1 WHERE activity_id = #{activityId}")
    int increaseStock(@Param("activityId") Long activityId);
}
