package dev.neeraj.exchange.matching;

import dev.neeraj.exchange.core.OrderStatus;
import dev.neeraj.exchange.core.OrderType;
import dev.neeraj.exchange.core.Side;

public final class OrderPoolTest {

    public static void main(String[] args) {
        System.out.println("================================================================================");
        System.out.println("               PHASE 2, STEP 1: ORDER POOL & FLYWEIGHT VERIFICATION             ");
        System.out.println("================================================================================");

        testPoolLifecycle();
        testFieldPersistenceAndIsolation();

        System.out.println("\n================================================================================");
        System.out.println("                     ALL ORDER POOL TESTS PASSED                                ");
        System.out.println("================================================================================");
    }

    private static void testPoolLifecycle() {
        System.out.println("  [Test] OrderPool FreeList Acquire & Release Lifecycle...");

        final int capacity = 100;
        OrderPool pool = new OrderPool(capacity);

        int[] acquired = new int[capacity];
        for (int i = 0; i < capacity; i++) {
            acquired[i] = pool.acquire();
            assert acquired[i] >= 0 && acquired[i] < capacity;
        }

        try {
            pool.acquire();
            assert false : "Expected exception on pool underflow";
        } catch (IllegalStateException expected) {}

        for (int i = 0; i < capacity; i++) {
            pool.release(acquired[i]);
        }

        System.out.println("  --> Result: PASSED");
    }

    private static void testFieldPersistenceAndIsolation() {
        System.out.println("  [Test] 128-Byte Field Persistence & Slot Isolation...");

        OrderPool pool = new OrderPool(10);
        int slotA = pool.acquire();
        int slotB = pool.acquire();

        Order cursorA = new Order(pool);
        Order cursorB = new Order(pool);

        cursorA.wrap(slotA);
        cursorA.setId(1001L);
        cursorA.setPrice(150_0000L);
        cursorA.setTimestamp(100_000_000L);
        cursorA.setTriggerPrice(149_0000L);
        cursorA.setQty(500);
        cursorA.setDisplayQty(100);
        cursorA.setHiddenQty(400);
        cursorA.setPeakQty(100);
        cursorA.setParentLevelIndex(5);
        cursorA.setNextIndex(8);
        cursorA.setParticipantId(999);
        cursorA.setSide(Side.BUY);
        cursorA.setType(OrderType.ICEBERG);
        cursorA.setStatus(OrderStatus.NEW);
        cursorA.setPrevIndex(2);
        cursorA.setOriginalQty(500);

        cursorB.wrap(slotB);
        cursorB.setId(2002L);
        cursorB.setPrice(155_0000L);
        cursorB.setTimestamp(200_000_000L);
        cursorB.setTriggerPrice(0L);
        cursorB.setQty(1000);
        cursorB.setDisplayQty(1000);
        cursorB.setHiddenQty(0);
        cursorB.setPeakQty(0);
        cursorB.setParentLevelIndex(12);
        cursorB.setNextIndex(-1);
        cursorB.setParticipantId(888);
        cursorB.setSide(Side.SELL);
        cursorB.setType(OrderType.LIMIT);
        cursorB.setStatus(OrderStatus.ACCEPTED);
        cursorB.setPrevIndex(-1);
        cursorB.setOriginalQty(1000);

        // Re-read slot A and verify isolation
        cursorA.wrap(slotA);
        assert cursorA.id() == 1001L;
        assert cursorA.price() == 150_0000L;
        assert cursorA.timestamp() == 100_000_000L;
        assert cursorA.triggerPrice() == 149_0000L;
        assert cursorA.qty() == 500;
        assert cursorA.displayQty() == 100;
        assert cursorA.hiddenQty() == 400;
        assert cursorA.peakQty() == 100;
        assert cursorA.parentLevelIndex() == 5;
        assert cursorA.nextIndex() == 8;
        assert cursorA.participantId() == 999;
        assert cursorA.side() == Side.BUY;
        assert cursorA.type() == OrderType.ICEBERG;
        assert cursorA.status() == OrderStatus.NEW;
        assert cursorA.prevIndex() == 2;
        assert cursorA.originalQty() == 500;

        // Re-read slot B
        cursorB.wrap(slotB);
        assert cursorB.id() == 2002L;
        assert cursorB.price() == 155_0000L;
        assert cursorB.qty() == 1000;
        assert cursorB.side() == Side.SELL;
        assert cursorB.type() == OrderType.LIMIT;
        assert cursorB.status() == OrderStatus.ACCEPTED;

        System.out.println("  --> Result: PASSED");
    }
}
