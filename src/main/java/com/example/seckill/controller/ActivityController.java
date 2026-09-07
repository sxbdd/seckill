package com.example.seckill.controller;

import com.example.seckill.common.Result;
import com.example.seckill.dto.ActivityVO;
import com.example.seckill.dto.PageResult;
import com.example.seckill.service.ActivityService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/seckill/activities")
@RequiredArgsConstructor
public class ActivityController {

    private final ActivityService activityService;

    @GetMapping
    public Result<PageResult<ActivityVO>> list(@RequestParam(defaultValue = "1") long page,
                                               @RequestParam(defaultValue = "10") long size) {
        return Result.ok(activityService.listUser(page, size));
    }

    @GetMapping("/{id}")
    public Result<ActivityVO> detail(@PathVariable Long id) {
        return Result.ok(activityService.detailUser(id));
    }
}
