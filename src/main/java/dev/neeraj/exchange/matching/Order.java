package dev.neeraj.exchange.matching;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import dev.neeraj.exchange.core.OrderStatus;
import dev.neeraj.exchange.core.OrderType;
import dev.neeraj.exchange.core.Side;

/**
 * Flyweight cursor over off-heap MemorySegment order slots.
 * Provides pure getters and setters for the 128-byte cache-aligned order layout.
 */
public class Order {
    private final MemorySegment segment;
    private long baseOffset = -1L;
    private int index = -1;

    public Order(MemorySegment segment) {
        this.segment = segment;
    }

    public Order(OrderPool pool) {
        this(pool.segment());
    }

    public Order wrap(int index) {
        this.index = index;
        this.baseOffset = (index >= 0) ? (((long) index) << OrderPool.ORDER_SHIFT) : -1L;
        return this;
    }

    public int index() {
        return index;
    }

    public boolean isNull() {
        return index < 0;
    }

    // --- Cache Line 1 (Hot: Offsets 0–63) ---

    public long id() {
        return segment.get(ValueLayout.JAVA_LONG, baseOffset + OrderPool.OFFSET_ID);
    }
    public void setId(long id) {
        segment.set(ValueLayout.JAVA_LONG, baseOffset + OrderPool.OFFSET_ID, id);
    }

    public long price() {
        return segment.get(ValueLayout.JAVA_LONG, baseOffset + OrderPool.OFFSET_PRICE);
    }
    public void setPrice(long price) {
        segment.set(ValueLayout.JAVA_LONG, baseOffset + OrderPool.OFFSET_PRICE, price);
    }

    public long timestamp() {
        return segment.get(ValueLayout.JAVA_LONG, baseOffset + OrderPool.OFFSET_TIMESTAMP);
    }
    public void setTimestamp(long timestamp) {
        segment.set(ValueLayout.JAVA_LONG, baseOffset + OrderPool.OFFSET_TIMESTAMP, timestamp);
    }

    public long triggerPrice() {
        return segment.get(ValueLayout.JAVA_LONG, baseOffset + OrderPool.OFFSET_TRIGGER_PRICE);
    }
    public void setTriggerPrice(long triggerPrice) {
        segment.set(ValueLayout.JAVA_LONG, baseOffset + OrderPool.OFFSET_TRIGGER_PRICE, triggerPrice);
    }

    public int qty() {
        return segment.get(ValueLayout.JAVA_INT, baseOffset + OrderPool.OFFSET_QTY);
    }
    public void setQty(int qty) {
        segment.set(ValueLayout.JAVA_INT, baseOffset + OrderPool.OFFSET_QTY, qty);
    }

    public int displayQty() {
        return segment.get(ValueLayout.JAVA_INT, baseOffset + OrderPool.OFFSET_DISPLAY_QTY);
    }
    public void setDisplayQty(int displayQty) {
        segment.set(ValueLayout.JAVA_INT, baseOffset + OrderPool.OFFSET_DISPLAY_QTY, displayQty);
    }

    public int hiddenQty() {
        return segment.get(ValueLayout.JAVA_INT, baseOffset + OrderPool.OFFSET_HIDDEN_QTY);
    }
    public void setHiddenQty(int hiddenQty) {
        segment.set(ValueLayout.JAVA_INT, baseOffset + OrderPool.OFFSET_HIDDEN_QTY, hiddenQty);
    }

    public int peakQty() {
        return segment.get(ValueLayout.JAVA_INT, baseOffset + OrderPool.OFFSET_PEAK_QTY);
    }
    public void setPeakQty(int peakQty) {
        segment.set(ValueLayout.JAVA_INT, baseOffset + OrderPool.OFFSET_PEAK_QTY, peakQty);
    }

    public int parentLevelIndex() {
        return segment.get(ValueLayout.JAVA_INT, baseOffset + OrderPool.OFFSET_PARENT_LEVEL_INDEX);
    }
    public void setParentLevelIndex(int parentLevelIndex) {
        segment.set(ValueLayout.JAVA_INT, baseOffset + OrderPool.OFFSET_PARENT_LEVEL_INDEX, parentLevelIndex);
    }

    public int nextIndex() {
        return segment.get(ValueLayout.JAVA_INT, baseOffset + OrderPool.OFFSET_NEXT_INDEX);
    }
    public void setNextIndex(int nextIndex) {
        segment.set(ValueLayout.JAVA_INT, baseOffset + OrderPool.OFFSET_NEXT_INDEX, nextIndex);
    }

    public int participantId() {
        return segment.get(ValueLayout.JAVA_INT, baseOffset + OrderPool.OFFSET_PARTICIPANT_ID);
    }
    public void setParticipantId(int participantId) {
        segment.set(ValueLayout.JAVA_INT, baseOffset + OrderPool.OFFSET_PARTICIPANT_ID, participantId);
    }

    public Side side() {
        return Side.fromOrdinal(rawSide());
    }
    public byte rawSide() {
        return segment.get(ValueLayout.JAVA_BYTE, baseOffset + OrderPool.OFFSET_SIDE);
    }
    public void setSide(Side side) {
        setSide((byte) side.ordinal());
    }
    public void setSide(byte side) {
        segment.set(ValueLayout.JAVA_BYTE, baseOffset + OrderPool.OFFSET_SIDE, side);
    }

    public OrderType type() {
        return OrderType.fromOrdinal(rawType());
    }
    public byte rawType() {
        return segment.get(ValueLayout.JAVA_BYTE, baseOffset + OrderPool.OFFSET_TYPE);
    }
    public void setType(OrderType type) {
        setType((byte) type.ordinal());
    }
    public void setType(byte type) {
        segment.set(ValueLayout.JAVA_BYTE, baseOffset + OrderPool.OFFSET_TYPE, type);
    }

    public OrderStatus status() {
        return OrderStatus.fromOrdinal(rawStatus());
    }
    public byte rawStatus() {
        return segment.get(ValueLayout.JAVA_BYTE, baseOffset + OrderPool.OFFSET_STATUS);
    }
    public void setStatus(OrderStatus status) {
        setStatus((byte) status.ordinal());
    }
    public void setStatus(byte status) {
        segment.set(ValueLayout.JAVA_BYTE, baseOffset + OrderPool.OFFSET_STATUS, status);
    }

    // --- Cache Line 2 (Cold: Offsets 64–127) ---

    public int prevIndex() {
        return segment.get(ValueLayout.JAVA_INT, baseOffset + OrderPool.OFFSET_PREV_INDEX);
    }
    public void setPrevIndex(int prevIndex) {
        segment.set(ValueLayout.JAVA_INT, baseOffset + OrderPool.OFFSET_PREV_INDEX, prevIndex);
    }

    public int originalQty() {
        return segment.get(ValueLayout.JAVA_INT, baseOffset + OrderPool.OFFSET_ORIGINAL_QTY);
    }
    public void setOriginalQty(int originalQty) {
        segment.set(ValueLayout.JAVA_INT, baseOffset + OrderPool.OFFSET_ORIGINAL_QTY, originalQty);
    }
}
