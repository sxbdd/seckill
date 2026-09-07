package com.example.seckill.utils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ThreadLocalRandom;

public final class OrderNoGenerator {
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

    private OrderNoGenerator() {
    }

    public static String next() {
        return LocalDateTime.now().format(FMT) + String.format("%06d", ThreadLocalRandom.current().nextInt(1_000_000));
    }
}
