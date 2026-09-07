package com.example.seckill.controller;

import com.example.seckill.common.Result;
import com.example.seckill.dto.GoodsVO;
import com.example.seckill.service.GoodsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/goods")
@RequiredArgsConstructor
public class GoodsController {

    private final GoodsService goodsService;

    @GetMapping("/{id}")
    public Result<GoodsVO> detail(@PathVariable Long id) {
        return Result.ok(goodsService.getById(id));
    }
}
