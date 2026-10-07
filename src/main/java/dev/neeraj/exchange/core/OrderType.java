package dev.neeraj.exchange.core;

public enum OrderType {
    LIMIT,
    MARKET,
    IOC,
    FOK,
    GTC,
    STOP,
    STOP_LIMIT,
    ICEBERG,
    POST_ONLY;

    private static final OrderType[] ALL = values();

    public static OrderType fromOrdinal(int ordinal) {
        return ALL[ordinal];
    }
}
