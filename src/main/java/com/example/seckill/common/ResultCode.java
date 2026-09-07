package com.example.seckill.common;

import lombok.Getter;

@Getter
public enum ResultCode {
    SUCCESS(200, "success"),
    PARAM_ERROR(400, "参数错误"),
    UNAUTHORIZED(401, "未登录或登录已过期"),
    FORBIDDEN(403, "无权限"),
    NOT_FOUND(404, "资源不存在"),
    TOO_MANY_REQUESTS(429, "系统繁忙，请稍后再试"),
    ACTIVITY_NOT_STARTED(1001, "活动未开始"),
    ACTIVITY_ENDED(1002, "活动已结束"),
    ACTIVITY_NOT_EXIST(1003, "活动不存在"),
    SOLD_OUT(2001, "手慢了，已被抢完"),
    REPEAT_PURCHASE(2002, "重复抢购"),
    SYSTEM_ERROR(500, "系统异常");

    private final Integer code;
    private final String message;

    ResultCode(Integer code, String message) {
        this.code = code;
        this.message = message;
    }
}
