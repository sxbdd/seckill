package com.example.seckill.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank(message = "用户名不能为空")
        @Size(min = 4, max = 50, message = "用户名长度需在4~50之间")
        String username,

        @NotBlank(message = "密码不能为空")
        @Size(min = 8, max = 64, message = "密码至少8位")
        String password,

        @Size(max = 50, message = "昵称最长50")
        String nickname) {
}
