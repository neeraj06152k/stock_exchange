package dev.neeraj.exchange;

import dev.neeraj.exchange.core.Types;
import dev.neeraj.exchange.core.Types.OrderType;
import dev.neeraj.exchange.core.Types.Side;
import dev.neeraj.exchange.matching.Event;
import dev.neeraj.exchange.matching.Event.EventType;
import dev.neeraj.exchange.matching.MatchingEngine;
import dev.neeraj.exchange.matching.MatchingEngine.InboundOrder;
import dev.neeraj.exchange.matching.MatchingEngine.RequestAction;

import java.util.List;

public class MatchingEngineTest {

    public static void main(String[] args) {
        System.out.println("================================================================================");
        System.out.println("                 MATCHING ENGINE FUNCTIONAL VERIFICATION SUITE                  ");
        System.out.println("================================================================================");

        testLimitOrderResting();
        testFullMatch();
        testIcebergPartialFills();
        testCancellation();
        testStopOrderTrigger();

        System.out.println("\n================================================================================");
        System.out.println("                     ALL 5 TESTS PASSED SUCCESSFULLY                            ");
        System.out.println("================================================================================");
    }

    private static void testLimitOrderResting() {
        printHeader("TEST 1: Passive Limit Order Placement (Resting on Book)");

        MatchingEngine engine = new MatchingEngine(List.of("AAPL"));
        var order = createOrder(101L, "AAPL", Side.BUY, OrderType.LIMIT, 150_0000L, 100);
        printCommand(order, "Submit Buy Limit: 100 shares @ $150.00");

        List<Event> events = engine.prepareOrder(order);
        printEvents(events);

        assert events.size() == 2 : "Expected 2 events (ACCEPTED, RESTED)";
        assert events.get(0).type() == EventType.ORDER_ACCEPTED;
        assert events.get(1).type() == EventType.ORDER_RESTED;
        assert engine.orderBook("AAPL").bestBidPrice() == 150_0000L;

        System.out.printf("  [Book State] Best Bid: $%.2f | Best Ask: $%.2f\n",
            Types.toDouble(engine.orderBook("AAPL").bestBidPrice()),
            Types.toDouble(engine.orderBook("AAPL").bestAskPrice()));
        System.out.println("  --> Result: PASSED");
    }

    private static void testFullMatch() {
        printHeader("TEST 2: Two Crossing Orders (Immediate Full Match)");

        MatchingEngine engine = new MatchingEngine(List.of("AAPL"));

        var buy = createOrder(201L, "AAPL", Side.BUY, OrderType.LIMIT, 150_0000L, 100);
        printCommand(buy, "Step 1: Maker places Buy Limit 100 @ $150.00");
        printEvents(engine.prepareOrder(buy));

        var sell = createOrder(202L, "AAPL", Side.SELL, OrderType.LIMIT, 150_0000L, 100);
        printCommand(sell, "Step 2: Taker places Sell Limit 100 @ $150.00 (Crosses Spread)");
        List<Event> events = engine.prepareOrder(sell);
        printEvents(events);

        assert events.stream().anyMatch(e -> e.type() == EventType.TRADE);
        assert engine.orderBook("AAPL").isEmpty() : "Book should be empty after full match";

        System.out.println("  [Book State] Book is completely empty (all volume executed)");
        System.out.println("  --> Result: PASSED");
    }

    private static void testIcebergPartialFills() {
        printHeader("TEST 3: Iceberg Order Execution & Display Reload");

        MatchingEngine engine = new MatchingEngine(List.of("AAPL"));
        
        var iceberg = createOrder(301L, "AAPL", Side.BUY, OrderType.ICEBERG, 150_0000L, 1000);
        iceberg.displayQty = 200;
        printCommand(iceberg, "Step 1: Place Iceberg (Total: 1000, Visible: 200, Hidden: 800) @ $150.00");
        printEvents(engine.prepareOrder(iceberg));

        System.out.printf("  [Book State Before Match] Level Total Visible Volume: %d shares\n",
            engine.orderBook("AAPL").bestBid().totalQty());
        assert engine.orderBook("AAPL").bestBid().totalQty() == 200;

        var sell = createOrder(302L, "AAPL", Side.SELL, OrderType.LIMIT, 150_0000L, 200);
        printCommand(sell, "Step 2: Incoming Sell 200 @ $150.00 (Eats the entire visible slice)");
        List<Event> events = engine.prepareOrder(sell);
        printEvents(events);

        System.out.printf("  [Book State After Match] Level Total Visible Volume: %d shares (Reloaded from hidden!)\n",
            engine.orderBook("AAPL").bestBid().totalQty());
        assert engine.orderBook("AAPL").bestBid().totalQty() == 200 : "Iceberg should reload 200 display qty";
        System.out.println("  --> Result: PASSED");
    }

    private static void testCancellation() {
        printHeader("TEST 4: Order Cancellation in O(1)");

        MatchingEngine engine = new MatchingEngine(List.of("AAPL"));
        var buy = createOrder(401L, "AAPL", Side.BUY, OrderType.LIMIT, 150_0000L, 100);
        printCommand(buy, "Step 1: Place resting Buy 100 @ $150.00");
        engine.prepareOrder(buy);

        var cancel = new InboundOrder();
        cancel.action = RequestAction.CANCEL;
        cancel.symbol = "AAPL";
        cancel.orderId = 401L;
        printCommand(cancel, "Step 2: Cancel Order #401");

        List<Event> events = engine.prepareOrder(cancel);
        printEvents(events);

        assert events.get(0).type() == EventType.ORDER_CANCELED;
        assert engine.orderBook("AAPL").isEmpty();
        System.out.println("  [Book State] Book empty after cancellation");
        System.out.println("  --> Result: PASSED");
    }

    private static void testStopOrderTrigger() {
        printHeader("TEST 5: Stop-Loss Order Trigger on Market Trade");

        MatchingEngine engine = new MatchingEngine(List.of("AAPL"));

        var stop = createOrder(501L, "AAPL", Side.SELL, OrderType.STOP, 0L, 50);
        stop.triggerPrice = 148_0000L;
        printCommand(stop, "Step 1: Arm Stop-Loss (Sell 50 if Trade Price <= $148.00)");
        printEvents(engine.prepareOrder(stop));

        var buy = createOrder(502L, "AAPL", Side.BUY, OrderType.LIMIT, 148_0000L, 100);
        printCommand(buy, "Step 2: Add Bid Liquidity (Buy 100 @ $148.00)");
        printEvents(engine.prepareOrder(buy));

        var trigger = createOrder(503L, "AAPL", Side.SELL, OrderType.MARKET, 0L, 10);
        printCommand(trigger, "Step 3: Market Sell 10 (Trades at $148.00 -> Triggers Stop #501)");
        List<Event> events = engine.prepareOrder(trigger);
        printEvents(events);

        assert events.stream().anyMatch(e -> e.type() == EventType.TRADE);
        System.out.printf("  [Book State] Remaining Bid Volume at $148.00: %d shares (100 - 10 trigger - 50 stop = 40)\n",
            engine.orderBook("AAPL").bestBid().totalQty());
        assert engine.orderBook("AAPL").bestBid().totalQty() == 40;
        System.out.println("  --> Result: PASSED");
    }

    // --- Output Formatting Helpers ---

    private static void printHeader(String title) {
        System.out.println("\n--------------------------------------------------------------------------------");
        System.out.println("  " + title);
        System.out.println("--------------------------------------------------------------------------------");
    }

    private static void printCommand(InboundOrder o, String desc) {
        System.out.printf("  [Command] %s\n", desc);
        if (o.action == RequestAction.ADD) {
            System.out.printf("            Details: %s %s %d %s | Price: $%.2f | Trigger: $%.2f | OrderId: %d\n",
                o.action, o.side, o.qty, o.orderType,
                Types.toDouble(o.price), Types.toDouble(o.triggerPrice), o.orderId);
        } else {
            System.out.printf("            Details: %s | OrderId: %d\n", o.action, o.orderId);
        }
    }

    private static void printEvents(List<Event> events) {
        System.out.println("  [Emitted Events]");
        for (Event e : events) {
            switch (e.type()) {
                case ORDER_ACCEPTED ->
                    System.out.printf("    Seq #%-3d | %-22s | Order #%-4d | %s %s %d @ $%.2f\n",
                        e.sequenceNumber(), e.type(), e.orderId(), e.side(), e.orderType(), e.qty(), Types.toDouble(e.price()));
                case ORDER_RESTED ->
                    System.out.printf("    Seq #%-3d | %-22s | Order #%-4d | Rested on Book: %d @ $%.2f\n",
                        e.sequenceNumber(), e.type(), e.orderId(), e.qty(), Types.toDouble(e.price()));
                case TRADE ->
                    System.out.printf("    Seq #%-3d | %-22s | Match #%-4d | Traded: %d shares @ $%.2f (Buyer: #%d <-> Seller: #%d)\n",
                        e.sequenceNumber(), e.type(), e.matchId(), e.qty(), Types.toDouble(e.price()), e.buyOrderId(), e.sellOrderId());
                case ORDER_FILLED ->
                    System.out.printf("    Seq #%-3d | %-22s | Order #%-4d | Filled %d shares @ $%.2f\n",
                        e.sequenceNumber(), e.type(), e.orderId(), e.qty(), Types.toDouble(e.price()));
                case ORDER_PARTIALLY_FILLED ->
                    System.out.printf("    Seq #%-3d | %-22s | Order #%-4d | Partially Filled %d shares (Remaining: %d)\n",
                        e.sequenceNumber(), e.type(), e.orderId(), e.qty(), e.remainingQty());
                case ORDER_CANCELED ->
                    System.out.printf("    Seq #%-3d | %-22s | Order #%-4d | Canceled: %d shares\n",
                        e.sequenceNumber(), e.type(), e.orderId(), e.qty());
                case ORDER_REJECTED ->
                    System.out.printf("    Seq #%-3d | %-22s | Order #%-4d | REJECTED: Reason = %s\n",
                        e.sequenceNumber(), e.type(), e.orderId(), e.reasonCode());
                default ->
                    System.out.printf("    Seq #%-3d | %-22s | Order #%-4d\n",
                        e.sequenceNumber(), e.type(), e.orderId());
            }
        }
    }

    private static InboundOrder createOrder(long id, String symbol, Side side, OrderType type, long price, int qty) {
        var o = new InboundOrder();
        o.action = RequestAction.ADD;
        o.orderId = id;
        o.symbol = symbol;
        o.side = side;
        o.orderType = type;
        o.price = price;
        o.qty = qty;
        o.timestamp = System.nanoTime();
        o.participant_id = 1L;
        o.displayQty = qty;
        return o;
    }
}
