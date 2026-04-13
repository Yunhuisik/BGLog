package com.pubgsite.bglog.dto;

public enum Period {

    DAY(1),
    WEEK(7),
    MONTH(30);

    private final int days;

    Period(int days) {
        this.days = days;
    }

    public int getDays() {
        return days;
    }

    // ✅ 문자열 안전 변환
    public static Period from(String value) {
        return switch (value.toUpperCase()) {
            case "DAY" -> DAY;
            case "WEEK" -> WEEK;
            case "MONTH" -> MONTH;
            default -> WEEK;
        };
    }
}
