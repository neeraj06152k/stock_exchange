package dev.neeraj.exchange.core;

public final class Price {
    private Price() {}

    public static final long SCALE = 10_000L;

    public static long of(double price) {
        return (long) Math.round(price * SCALE);
    }

    public static double toDouble(long price) {
        return (double) price / SCALE;
    }
}
