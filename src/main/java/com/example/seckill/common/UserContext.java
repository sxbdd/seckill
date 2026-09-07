package com.example.seckill.common;

public final class UserContext {
    private static final ThreadLocal<Long> USER_ID = new ThreadLocal<>();
    private static final ThreadLocal<String> ROLE = new ThreadLocal<>();

    private UserContext() {
    }

    public static void set(Long userId, String role) {
        USER_ID.set(userId);
        ROLE.set(role);
    }

    public static Long userId() {
        return USER_ID.get();
    }

    public static String role() {
        return ROLE.get();
    }

    public static boolean isAdmin() {
        return "ADMIN".equals(ROLE.get());
    }

    public static void clear() {
        USER_ID.remove();
        ROLE.remove();
    }
}
