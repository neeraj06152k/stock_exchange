package dev.neeraj.exchange.core;

public enum OrderStatus {
    NEW,
    ACCEPTED,
    PARTIALLY_FILLED,
    FILLED,
    CANCELED;

    private static final OrderStatus[] ALL = values();

    public static OrderStatus fromOrdinal(int ordinal) {
        return ALL[ordinal];
    }
}
