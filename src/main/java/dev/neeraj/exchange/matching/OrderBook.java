package dev.neeraj.exchange.matching;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.TreeMap;

import dev.neeraj.exchange.core.Types;
import dev.neeraj.exchange.matching.Event.ReasonCode;
import dev.neeraj.exchange.core.Types.OrderType;
import dev.neeraj.exchange.core.Types.Side;

public class OrderBook {

    private final String symbol;

    //Bids: Highest price first, Asks: Lowest Price First
    private final NavigableMap<Long, Level> bids = new TreeMap<>(Comparator.reverseOrder());
    private final NavigableMap<Long, Level> asks = new TreeMap<>(Comparator.naturalOrder());

    // Order Index by ID
    private final Map<Long, Order> orders = new HashMap<>();

    // Stop order registries - Indexed by price
    private final NavigableMap<Long, List<Order>> stopBuys  = new TreeMap<>(Comparator.naturalOrder());
    private final NavigableMap<Long, List<Order>> stopSells = new TreeMap<>(Comparator.reverseOrder());

    //Monotonic Sequences
    private long _sequenceNumber = 1L;
    private long _matchId = 1L;
    private long nextSequenceNumber(){return _sequenceNumber++;}
    private long nextMatchId(){return _matchId++;}

    //Event output buffer
    private final List<Event> emittedEvents = new ArrayList<>();

    public OrderBook(String symbol){
        this.symbol = symbol;
    }

    // Book queries

    public String symbol(){ return symbol;}

    public Level bestBid(){
        var entry = bids.firstEntry();
        return entry!=null?entry.getValue():null;
    }

    public Level bestAsk(){
        var entry = asks.firstEntry();
        return entry!=null?entry.getValue():null;
    }

    public long bestBidPrice(){
        return Optional.ofNullable(bestBid()).map(lvl->lvl.price()).orElse(0L);
    }

    public long bestAskPrice(){
        return Optional.ofNullable(bestAsk()).map(lvl->lvl.price()).orElse(0L);
    }

    public boolean isEmpty(){
        return bids.isEmpty() && asks.isEmpty();
    }

    public Order findById(long orderId){
        return orders.get(orderId);
    }

    // validation methods

    public Optional<ReasonCode> validateNewOrder(
        long orderId, Side side, Types.OrderType orderType, long price, 
        int qty, int displayQty, long triggerPrice
    ){
        assert(side!=null);
        assert(orderType!=null);

        if(orders.containsKey(orderId)) return Optional.of(ReasonCode.DUPLICATE_ORDER_ID);
        if(qty<=0) return Optional.of(ReasonCode.INVALID_QUANTITY);

        if(price<0) return Optional.of(ReasonCode.NEGATIVE_PRICE);
        if(price==0 && !orderType.equals(OrderType.MARKET) && !orderType.equals(OrderType.STOP)) return Optional.of(ReasonCode.NEGATIVE_PRICE);

        // Market order on Empty book
        if(orderType.equals(OrderType.MARKET) 
            && (side.equals(Side.BUY)?asks.isEmpty():bids.isEmpty())) return Optional.of(ReasonCode.BOOK_EMPTY);
        
        // Post-Only crossing check
        if(orderType.equals(OrderType.POST_ONLY) 
            && wouldTakerCrossMaker(side, price)) return Optional.of(ReasonCode.POST_ONLY_WOULD_CROSS);
        
        // Iceberg Display validation
        if(orderType.equals(OrderType.ICEBERG) && (displayQty<=0||displayQty>=qty))
            return Optional.of(ReasonCode.INVALID_ICEBERG_DISPLAY);

        // FOK liquidity check
        if(orderType.equals(OrderType.FOK) && availableLiquidity(side, price) < qty){
            return Optional.of(ReasonCode.FOK_INSUFFICIENT_LIQUIDITY);
        }

        return Optional.empty();
    }

    // Core Methods

    public List<Event> addOrder(
        long participantId, long timestamp, long orderId, OrderType orderType, 
        Side side, long price, long triggerPrice, 
        int quantity, int displayQuantity
    ){

        emittedEvents.clear();

        // Validation
        var rejection = validateNewOrder(orderId, side, orderType, price, quantity, displayQuantity, triggerPrice);

        if(rejection.isPresent()){
            emitRejected(timestamp, orderId, rejection.get());
            return emittedEvents;
        }

        // Build Order
        Order order = Order.builder()
            .id(orderId)
            .side(side)
            .type(orderType)
            .price(price)
            .qty(quantity)
            .timestamp(timestamp)
            .participantId(participantId)
            .triggerPrice(triggerPrice)
            .displayQty(displayQuantity)
            .build();

        orders.put(orderId, order);

        // Stop Order routing
        if(order.isStopOrder()){
            parkStopOrder(order);
            emitAccepted(timestamp, order);
            return emittedEvents;
        }

        // Inbound Matching Execution
        executeInboundOrder(order, timestamp);
        
        return emittedEvents;
    }

    public List<Event> modifyOrder(long orderId, int newQty, long newPrice, long timestamp){
        emittedEvents.clear();

        var order = orders.get(orderId);
        
        // Validation
        ReasonCode errReasonCode = ReasonCode.NONE;

        if(order==null){
            errReasonCode = ReasonCode.ORDER_NOT_FOUND;
        }
        else if(order.isStopOrder()){
            errReasonCode = ReasonCode.INVALID_MODIFICATION;
        }
        else if (newQty<=0) {
            errReasonCode = ReasonCode.INVALID_QUANTITY;
        }
        else if (newPrice<0) {
            errReasonCode = ReasonCode.NEGATIVE_PRICE;
        }

        if(!errReasonCode.equals(ReasonCode.NONE)){
            emitRejected(timestamp, orderId, errReasonCode);
            return emittedEvents;
        }

        // Volume Reduction
        if(newPrice==order.price() && newQty<order.qty()){
            order.parentLevel.totalQty -= (order.qty()-newQty);
            order.setQty(newQty);
            emitReduced(timestamp, orderId, newQty);
        }
        else if (newPrice == order.price() && newQty == order.qty()) {
            return emittedEvents;
        }

        // Price change or quantity increase -> Cancel + Replace (Loses time priority)
        // 1. Unlink from existing level
        if (order.parentLevel != null) {
            Level level = order.parentLevel;
            level.removeOrder(order);
            if (level.isEmpty()) {
                if (order.side() == Side.BUY) bids.remove(level.price());
                else asks.remove(level.price());
            }
        }
        emitCanceled(timestamp, orderId, order.qty());
        // 2. Build replacement with updated parameters
        Order replacement = Order.builder()
            .id(order.id())
            .side(order.side())
            .type(order.type())
            .price(newPrice)
            .qty(newQty)
            .timestamp(timestamp)
            .participantId(order.participantId())
            .triggerPrice(order.triggerPrice())
            .displayQty(newQty)
            .build();
        orders.put(orderId, replacement);
        // 3. Re-enter matching pipeline
        executeInboundOrder(replacement, timestamp);

        return emittedEvents;
    }

    public List<Event> cancelOrder(long orderId, long timestamp) {
        emittedEvents.clear();

        Order order = orders.get(orderId);
        if (order == null) {
            emitRejected(timestamp, orderId, ReasonCode.ORDER_NOT_FOUND);
            return emittedEvents;
        }

        // Remove from active level or stop registry
        if (order.isStopOrder()) {
            var map = (order.side() == Side.BUY) ? stopBuys : stopSells;
            List<Order> list = map.get(order.triggerPrice());
            if (list != null) {
                list.remove(order);
                if (list.isEmpty()) map.remove(order.triggerPrice());
            }
        } else if (order.parentLevel != null) {
            Level level = order.parentLevel;
            level.removeOrder(order);
            if (level.isEmpty()) {
                if (order.side() == Side.BUY) bids.remove(level.price());
                else asks.remove(level.price());
            }
        }

        int canceledQty = order.qty();
        order.cancel();
        orders.remove(orderId);

        emitCanceled(timestamp, orderId, canceledQty);
        return emittedEvents;
    }



    // private methods
    private boolean wouldTakerCrossMaker(Side side, long price){
        return switch(side){
            case Side.BUY  -> bestAsk()!=null && bestAskPrice()<=price;
            case Side.SELL -> bestBid()!=null && bestBidPrice()>=price;
        };
    }

    private int availableLiquidity(Side side, long limitPrice){
        long available = switch(side) {
            case BUY -> asks.entrySet().stream()
                        .filter(e->e.getKey()<=limitPrice).mapToLong(e->e.getValue().totalQty).sum();
            case SELL -> bids.entrySet().stream()
                        .filter(e->e.getKey()>=limitPrice).mapToLong(e->e.getValue().totalQty).sum();
        };

        return (int) Math.min(available, Integer.MAX_VALUE);
    }

    

    private boolean allowsResting(OrderType type){
        return switch(type){
            case LIMIT, GTC, ICEBERG, POST_ONLY -> true;
            default -> false;
        };
    }


    private void executeInboundOrder(Order order, long eventTimestamp){
        assert(order!=null);
        emitAccepted(eventTimestamp, order);

        long lastTradedPrice = 0L;
        
        while(!order.isFilled()){
            var oppLevel = order.side().equals(Side.BUY)?bestAsk():bestBid();
            if(oppLevel==null) break;

            if(!order.type().equals(OrderType.MARKET) && 
                !wouldTakerCrossMaker(order.side(), order.price())) break;

            lastTradedPrice = oppLevel.price();
            matchAgainstLevel(order, oppLevel, eventTimestamp);

            // Evict depleted Level
            if(oppLevel.isEmpty()) {
                if(order.side().equals(Side.BUY)) asks.remove(oppLevel.price());
                else bids.remove(oppLevel.price());
            }
        }

        // Post-match handling
        if (order.isFilled()) {
            orders.remove(order.id());
        } else {
            if (allowsResting(order.type())) {
                restOrder(order, eventTimestamp);
            } else {
                emitCanceled(eventTimestamp, order.id(), order.qty());
                orders.remove(order.id());
            }
        }
        // Trigger stop orders after the current taker has finished full execution
        if(lastTradedPrice!=0L) triggerStopOrders(lastTradedPrice, eventTimestamp);
    }   

    private void matchAgainstLevel(Order taker, Level level, long timestamp){
        while(!taker.isFilled() && !level.isEmpty()){
            matchAgainstLevelHelper(taker, level, timestamp);
        }
    }

    private void matchAgainstLevelHelper(Order taker, Level level, long timestamp){
        assert(wouldTakerCrossMaker(taker.side(), taker.price()));
        Order maker = level.front();

        int matchQty = Math.min(taker.qty(), maker.displayQty());
        long tradePrice = maker.price();

        long buyId  = taker.side().equals(Side.BUY)  ? taker.id():maker.id();
        long sellId = taker.side().equals(Side.SELL) ? taker.id():maker.id();
        emitTrade(timestamp, nextMatchId(), buyId, sellId, tradePrice, matchQty);

        taker.executeFill(matchQty);
        maker.executeFill(matchQty);
        level.totalQty -= matchQty;

        if(maker.isFilled()){
            emitFilled(timestamp, maker.id(), matchQty, tradePrice);
            level.removeOrder(maker);
            orders.remove(maker.id());
        } else {
            emitPartiallyFilled(timestamp, maker.id(), matchQty, maker.qty(), tradePrice);

            // Iceberg reload: reloaded displayQty is added to level total
            if(maker.isIceberg() && maker.displayQty() > 0){
                level.totalQty += maker.displayQty();
                if (level.orderCount > 1) {
                    level.removeOrder(maker);
                    level.totalQty += maker.displayQty();
                    level.addOrder(maker);
                }
            }
        }
    }

    private void triggerStopOrders(long lastTradePrice, long timestamp) {
        boolean triggeredAny;
        do {
            triggeredAny = false;

            // Buy Stops: triggerPrice <= lastTradePrice
            var buyView = stopBuys.headMap(lastTradePrice, true);
            if (!buyView.isEmpty()) {
                List<Order> toActivate = new ArrayList<>();
                buyView.values().forEach(toActivate::addAll);
                buyView.clear();

                for (Order o : toActivate) {
                    activateStop(o, timestamp);
                    triggeredAny = true;
                }
            }

            // Sell Stops: triggerPrice >= lastTradePrice
            var sellView = stopSells.headMap(lastTradePrice, true);
            if (!sellView.isEmpty()) {
                List<Order> toActivate = new ArrayList<>();
                sellView.values().forEach(toActivate::addAll);
                sellView.clear();

                for (Order o : toActivate) {
                    activateStop(o, timestamp);
                    triggeredAny = true;
                }
            }
        } while (triggeredAny);
    }

    private void activateStop(Order order, long timestamp) {
        // Convert to active type
        if (order.type() == OrderType.STOP) {
            order.setPrice(0L); // Market
            // OrderType remains or transitions to MARKET
        }
        executeInboundOrder(order, timestamp);
    }

    private void restOrder(Order order, long timestamp){
        var map = order.side().equals(Side.BUY)?bids:asks;
        Level level = map.computeIfAbsent(order.price(), Level::new);
        level.addOrder(order);
        emitRested(timestamp, order);
    }

    private void parkStopOrder(Order order){
        var map = order.side().equals(Side.BUY)?stopBuys:stopSells;
        map.computeIfAbsent(order.triggerPrice(), _ -> new ArrayList<>()).add(order);
    }



    // Emitters

    private void emitTrade(long timestamp, long matchId, long buyId, long sellId, long price, int qty) {
        var e = new Event();
        e.setTrade(nextSequenceNumber(), timestamp, matchId, symbol, buyId, sellId, price, qty);
        emittedEvents.add(e);
    }

    private void emitFilled(long timestamp, long orderId, int filledQty, long price) {
        var e = new Event();
        e.setOrderFilled(nextSequenceNumber(), timestamp, orderId, filledQty, price);
        emittedEvents.add(e);
    }

    private void emitPartiallyFilled(long timestamp, long orderId, int filledQty, int remainingQty, long price) {
        var e = new Event();
        e.setOrderPartiallyFilled(nextSequenceNumber(), timestamp, orderId, filledQty, remainingQty, price);
        emittedEvents.add(e);
    }

    private void emitRested(long timestamp, Order order) {
        var e = new Event();
        e.setOrderRested(nextSequenceNumber(), timestamp, order.id(), symbol,
            order.side(), order.price(), order.displayQty());
        emittedEvents.add(e);
    }

    private void emitCanceled(long timestamp, long orderId, int canceledQty) {
        var e = new Event();
        e.setOrderCanceled(nextSequenceNumber(), timestamp, orderId, canceledQty);
        emittedEvents.add(e);
    }

    private void emitRejected(long timestamp, long orderId, ReasonCode reason){
        var e =new Event();
        e.setOrderRejected(nextSequenceNumber(), timestamp, orderId, reason);
        emittedEvents.add(e);
    }

    private void emitAccepted(long timestamp, Order order){
        var e = new Event();
        e.setOrderAccepted(
            nextSequenceNumber(), timestamp, order.id(), symbol,
            order.side(), order.price(), order.qty(), order.type()
        );
        emittedEvents.add(e);
    }

    private void emitReduced(long timestamp, long orderId, int newQty){
        var e = new Event();
        e.setOrderReduced(nextSequenceNumber(), timestamp, orderId, newQty);
        emittedEvents.add(e);
    }


}
