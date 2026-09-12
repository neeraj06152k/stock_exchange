package dev.neeraj.exchange.matching;

import dev.neeraj.exchange.core.Types.OrderStatus;
import dev.neeraj.exchange.core.Types.OrderType;
import dev.neeraj.exchange.core.Types.Side;

public class Order {

    // --- 1. Immutable Order Metadata (Never change after creation) ---
    private final long id;
    private final Side side;
    private final OrderType type;
    private final long timestamp;
    private final long participantId;
    private final int originalQty;


    
    // --- 2. Order Parameters & Mutable Lifecycle State ---
    private long price;               // Can be modified if order is amended
    private int qty;                  // Current remaining quantity
    private OrderStatus status;       // NEW, ACCEPTED, PARTIALLY_FILLED, etc.
    
    // Stop & Iceberg parameters
    private final long triggerPrice;  // For STOP / STOP_LIMIT orders
    private int displayQty;           // Visible size on book (Iceberg)
    private int hiddenQty;            // Undisclosed size (Iceberg)
    private final int peakQty;        // Reload size (Iceberg)

    // --- 3. Intrusive Linked-List Pointers (Package-Private) ---
    // Package-private so Level and OrderBook can link/unlink orders directly in O(1)
    Order prev;
    Order next;
    Level parentLevel;

    // Constructor
    private Order (Builder b){
        this.id = b.id;
        this.side = b.side;
        this.type = b.type;
        this.price = b.price;
        this.originalQty = b.qty;
        this.qty = b.qty;
        this.timestamp = b.timestamp;
        this.participantId = b.participantId;
        this.status = OrderStatus.NEW;
        this.triggerPrice = b.triggerPrice;

        // Iceberg calculation
        if (b.displayQty > 0 && b.displayQty < b.qty) {
            this.displayQty = b.displayQty;
            this.hiddenQty = b.qty - b.displayQty;
            this.peakQty = b.displayQty;
        } else {
            this.displayQty = b.qty;
            this.hiddenQty = 0;
            this.peakQty = 0;
        }

    }

    // getters
    public long id() { return id; }
    public Side side() { return side; }
    public OrderType type() { return type; }
    public long price() { return price; }
    public int qty() { return qty; }
    public int originalQty() { return originalQty; }
    public long timestamp() { return timestamp; }
    public long participantId() { return participantId; }
    public OrderStatus status() { return status; }
    public long triggerPrice() { return triggerPrice; }
    public int displayQty() { return displayQty; }
    public int hiddenQty() { return hiddenQty; }
    public int peakQty() { return peakQty; }

    
    // methods

    public static Builder builder(){
        return new Builder();
    }

    public boolean isFilled(){
        return qty==0;
    }

    public boolean isIceberg(){
        return type==OrderType.ICEBERG;
    }


    public boolean isStopOrder(){
        return type == OrderType.STOP || type == OrderType.STOP_LIMIT;
    }

    public void executeFill(int fillQty){
        assert(fillQty<=this.qty);

        this.qty -= fillQty;
        this.displayQty -= fillQty;

        
        if(this.qty==0){
            this.status = OrderStatus.FILLED;
        } else {
            this.status = OrderStatus.PARTIALLY_FILLED;

            if(isIceberg() && this.displayQty==0){
                var reload = Math.min(this.peakQty, this.hiddenQty);
                this.displayQty = reload;
                this.hiddenQty -= reload;
            }
        }
    }

    public void cancel(){this.status = OrderStatus.CANCELLED;}

    public void setPrice(long price){this.price = price;}
    public void setQty(int qty){this.qty = qty;}
    public void setStatus(OrderStatus status){this.status = status;}



    // Builder

    public static class Builder {
        private long id;
        private Side side;
        private OrderType type = OrderType.LIMIT;
        private long price;
        private int qty;
        private long timestamp;
        private long participantId;
        private long triggerPrice = 0L;
        private int displayQty = 0;

        private Builder() {}

        public Builder id(long id) {
            this.id = id;
            return this;
        }

        public Builder side(Side side) {
            this.side = side;
            return this;
        }

        public Builder type(OrderType type) {
            this.type = type;
            return this;
        }

        public Builder price(long price) {
            this.price = price;
            return this;
        }

        public Builder qty(int qty) {
            this.qty = qty;
            return this;
        }

        public Builder timestamp(long timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public Builder participantId(long participantId) {
            this.participantId = participantId;
            return this;
        }

        public Builder triggerPrice(long triggerPrice) {
            this.triggerPrice = triggerPrice;
            return this;
        }

        public Builder displayQty(int displayQty) {
            this.displayQty = displayQty;
            return this;
        }

        public Order build() {
            return new Order(this);
        }
    }

}
