package dev.neeraj.exchange.matching;

import dev.neeraj.exchange.core.Types.OrderType;
import dev.neeraj.exchange.core.Types.ReasonCode;
import dev.neeraj.exchange.core.Types.Side;

public class Event {

    public enum EventType {
        ORDER_ACCEPTED,
        ORDER_RESTED,
        ORDER_REJECTED,
        ORDER_REDUCED,
        ORDER_PARTIALLY_FILLED,
        ORDER_FILLED,
        ORDER_CANCELED,
        TRADE
    }

    // --- 1. Event Type Tag ---
    private EventType type;
    // --- 2. Common Fields ---
    private long sequenceNumber;
    private long timestamp;
    private long orderId;
    private String symbol;
    private Side side;
    private long price;
    private int qty;
    private OrderType orderType;
    private ReasonCode reasonCode;
    // Partial fill / Cancel specific
    private int remainingQty;
    // Trade specific
    private long matchId;
    private long buyOrderId;
    private long sellOrderId;

    // --- 3. Mutation Methods (Overwriting in-place with ZERO allocations) ---
     public void setOrderAccepted(long seq, long ts, long orderId, String symbol,
                                 Side side, long price, int qty, OrderType orderType) {
        this.type = EventType.ORDER_ACCEPTED;
        this.sequenceNumber = seq;
        this.timestamp = ts;
        this.orderId = orderId;
        this.symbol = symbol;
        this.side = side;
        this.price = price;
        this.qty = qty;
        this.orderType = orderType;
    }
    public void setOrderRested(long seq, long ts, long orderId, String symbol,
                               Side side, long price, int qty) {
        this.type = EventType.ORDER_RESTED;
        this.sequenceNumber = seq;
        this.timestamp = ts;
        this.orderId = orderId;
        this.symbol = symbol;
        this.side = side;
        this.price = price;
        this.qty = qty;
    }
    public void setOrderRejected(long seq, long ts, long orderId, ReasonCode reason) {
        this.type = EventType.ORDER_REJECTED;
        this.sequenceNumber = seq;
        this.timestamp = ts;
        this.orderId = orderId;
        this.reasonCode = reason;
    }
    public void setOrderReduced(long seq, long ts, long orderId, int newQty) {
        this.type = EventType.ORDER_REDUCED;
        this.sequenceNumber = seq;
        this.timestamp = ts;
        this.orderId = orderId;
        this.qty = newQty;
    }
    public void setOrderPartiallyFilled(long seq, long ts, long orderId,
                                        int filledQty, int remainingQty, long price) {
        this.type = EventType.ORDER_PARTIALLY_FILLED;
        this.sequenceNumber = seq;
        this.timestamp = ts;
        this.orderId = orderId;
        this.qty = filledQty;
        this.remainingQty = remainingQty;
        this.price = price;
    }
    public void setOrderFilled(long seq, long ts, long orderId, int filledQty, long price) {
        this.type = EventType.ORDER_FILLED;
        this.sequenceNumber = seq;
        this.timestamp = ts;
        this.orderId = orderId;
        this.qty = filledQty;
        this.price = price;
    }
    public void setOrderCanceled(long seq, long ts, long orderId, int canceledQty) {
        this.type = EventType.ORDER_CANCELED;
        this.sequenceNumber = seq;
        this.timestamp = ts;
        this.orderId = orderId;
        this.qty = canceledQty;
    }
    public void setTrade(long seq, long ts, long matchId, String symbol,
                         long buyOrderId, long sellOrderId, long price, int qty) {
        this.type = EventType.TRADE;
        this.sequenceNumber = seq;
        this.timestamp = ts;
        this.matchId = matchId;
        this.symbol = symbol;
        this.buyOrderId = buyOrderId;
        this.sellOrderId = sellOrderId;
        this.price = price;
        this.qty = qty;
    }


    // --- 4. Getters ---
    public EventType type() { return type; }
    public long sequenceNumber() { return sequenceNumber; }
    public long timestamp() { return timestamp; }
    public long orderId() { return orderId; }
    public String symbol() { return symbol; }
    public Side side() { return side; }
    public long price() { return price; }
    public int qty() { return qty; }
    public int remainingQty() { return remainingQty; }
    public OrderType orderType() { return orderType; }
    public ReasonCode reasonCode() { return reasonCode; }
    public long matchId() { return matchId; }
    public long buyOrderId() { return buyOrderId; }
    public long sellOrderId() { return sellOrderId; }    
    
}
