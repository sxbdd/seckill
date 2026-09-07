package com.example.seckill.dto;

import java.util.List;

public record PageResult<T>(long total, List<T> list) {
}
