package dev.neeraj.exchange.matching;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import dev.neeraj.exchange.core.Types.OrderType;
import dev.neeraj.exchange.core.Types.Side;

public class MatchingEngine {
    private final Map<String, OrderBook> books;

    public MatchingEngine(List<String> symbols){
        books = new HashMap<>(symbols.size()*2);
        for(var symbol: symbols){
            var book = new OrderBook(symbol);
            books.putIfAbsent(symbol, book);
        }
    }

    public List<Event> prepareOrder(InboundOrder order){
        var orderBook = books.get(order.symbol);
        if(orderBook==null) return Collections.emptyList();
        
        return switch (order.action) {
            case ADD -> orderBook.addOrder(order.participant_id, order.timestamp, order.orderId, 
                    order.orderType, order.side, order.price, 
                    order.triggerPrice, order.qty, order.displayQty);
            case CANCEL-> orderBook.cancelOrder(order.orderId, order.timestamp);
            case MODIFY-> orderBook.modifyOrder(order.orderId, order.qty, order.price, order.timestamp);
        };
    }

    public OrderBook orderBook(String symbol){
        return books.get(symbol);
    }


    // Inner Class

    public static enum RequestAction{
        ADD,
        CANCEL,
        MODIFY
    }

    public static class InboundOrder{
        public RequestAction action = RequestAction.ADD;
        public String symbol = "";
        public long orderId = 0;
        public Side side = Side.BUY;
        public long price = 0;
        public int qty = 0;
        public OrderType orderType = OrderType.LIMIT;
        public long timestamp = 0;
        public long participant_id = 0;
        public long triggerPrice = 0;
        public int displayQty = 0;
    };

}
