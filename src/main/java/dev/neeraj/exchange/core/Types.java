package dev.neeraj.exchange.core;

import java.util.Optional;

public final class Types {
    private Types(){}

    // long price;
    // int quantity;
    // long orderId;
    // long sequenceNumber;
    // long timestamp;
    // int participantId;
    // int matchId;
    // String symbol;

    public static final long PRICE_SCALE = 10_000L;

    public enum Side {
        BUY, SELL;
        public static Side opposite(Side side){
            assert(side!=null);
            return side.equals(Side.BUY)?Side.SELL:Side.BUY;
        }
    }

    public enum OrderStatus {
        NEW,
        ACCEPTED,
        PARTIALLY_FILLED,
        FILLED,
        CANCELLED
    }

    public enum OrderType {
        MARKET,
        LIMIT,
        IOC,
        FOK,
        GTC,
        STOP,
        STOP_LIMIT,
        ICEBERG,
        POST_ONLY
    }


    public static long toPrice(double p){
        return (long) (p*PRICE_SCALE);
    }

    public static double toDouble(long price){
        return Double.valueOf((double)price/PRICE_SCALE);
    }
    
}