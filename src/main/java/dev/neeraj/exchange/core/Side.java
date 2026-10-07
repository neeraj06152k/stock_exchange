package dev.neeraj.exchange.core;

public enum Side {
    BUY,
    SELL;

    private static final Side[] ALL = values();

    public static Side fromOrdinal(int ordinal){
        return ALL[ordinal];
    }

    public Side opposite(){
        return this==BUY?SELL:BUY;
    }
}
