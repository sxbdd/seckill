package com.example.seckill.controller;

import com.example.seckill.common.BusinessException;
import com.example.seckill.common.Result;
import com.example.seckill.common.ResultCode;
import com.example.seckill.common.UserContext;
import com.example.seckill.dto.ActivityCreateRequest;
import com.example.seckill.dto.ActivityVO;
import com.example.seckill.dto.PageResult;
import com.example.seckill.dto.StockUpdateRequest;
import com.example.seckill.service.ActivityService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final ActivityService activityService;

    @PostMapping("/activities")
    public Result<Map<String, Long>> create(@Valid @RequestBody ActivityCreateRequest request) {
        requireAdmin();
        Long activityId = activityService.create(request);
        return Result.ok(Map.of("activityId", activityId));
    }

    @GetMapping("/activities")
    public Result<PageResult<ActivityVO>> list(@RequestParam(defaultValue = "1") long page,
                                               @RequestParam(defaultValue = "10") long size) {
        requireAdmin();
        return Result.ok(activityService.listAdmin(page, size));
    }

    @GetMapping("/activities/{id}")
    public Result<ActivityVO> detail(@PathVariable Long id) {
        requireAdmin();
        return Result.ok(activityService.detailAdmin(id));
    }

    @PostMapping("/activities/{id}/stock/reload")
    public Result<Map<String, Integer>> reload(@PathVariable Long id) {
        requireAdmin();
        return Result.ok(activityService.reload(id));
    }

    @PostMapping("/activities/{id}/end")
    public Result<Void> endNow(@PathVariable Long id) {
        requireAdmin();
        activityService.endNow(id);
        return Result.ok();
    }

    @PostMapping("/activities/{id}/stock")
    public Result<Map<String, Integer>> updateStock(@PathVariable Long id,
                                                    @Valid @RequestBody StockUpdateRequest request) {
        requireAdmin();
        activityService.updateStock(id, request.stock());
        return Result.ok(activityService.reload(id));
    }

    @DeleteMapping("/activities/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        requireAdmin();
        activityService.deleteActivity(id);
        return Result.ok();
    }

    private void requireAdmin() {
        if (!UserContext.isAdmin()) {
            throw new BusinessException(ResultCode.FORBIDDEN);
        }
    }
}
