package com.example.seckill.dto;

public record LoginResponse(String token, Long userId, String nickname, String role) {
}
