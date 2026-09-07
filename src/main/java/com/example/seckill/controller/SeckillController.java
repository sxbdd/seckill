package com.example.seckill.controller;

import com.example.seckill.common.Result;
import com.example.seckill.common.UserContext;
import com.example.seckill.dto.SeckillResult;
import com.example.seckill.service.SeckillService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/seckill")
@RequiredArgsConstructor
public class SeckillController {

    private final SeckillService seckillService;

    @PostMapping("/{activityId}/orders")
    public Result<SeckillResult> seckill(@PathVariable Long activityId) {
        String orderNo = seckillService.grab(activityId, UserContext.userId());
        return Result.ok(new SeckillResult(orderNo, 0));
    }
}
