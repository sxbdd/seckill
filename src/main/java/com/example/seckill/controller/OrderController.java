package com.example.seckill.controller;

import com.example.seckill.common.Result;
import com.example.seckill.common.UserContext;
import com.example.seckill.dto.OrderVO;
import com.example.seckill.dto.PageResult;
import com.example.seckill.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @GetMapping("/mine")
    public Result<PageResult<OrderVO>> mine(@RequestParam(defaultValue = "1") long page,
                                            @RequestParam(defaultValue = "10") long size) {
        return Result.ok(orderService.mine(page, size, UserContext.userId()));
    }

    @GetMapping("/{orderNo}")
    public Result<OrderVO> detail(@PathVariable String orderNo) {
        return Result.ok(orderService.detail(orderNo, UserContext.userId()));
    }

    @PostMapping("/{orderNo}/cancel")
    public Result<OrderVO> cancel(@PathVariable String orderNo) {
        return Result.ok(orderService.cancel(orderNo, UserContext.userId()));
    }

    @PostMapping("/{orderNo}/pay")
    public Result<OrderVO> pay(@PathVariable String orderNo) {
        return Result.ok(orderService.pay(orderNo, UserContext.userId()));
    }
}
