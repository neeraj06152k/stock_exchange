package dev.neeraj.exchange.matching;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.util.stream.IntStream;

/* 
 * Contiguous off-heap FFM pool for 128-byte orders.
 * Contains getters and setters for order fields.
 * To be accessed with flyweight pattern Order class.
 */
public final class OrderPool {
    public static final int ORDER_BYTES = 128;
    public static final int ORDER_SHIFT = 7;
    public static final int BYTE_ALLIGNMENT = 64;

    // Cache Line 1 (Hot)
    public static final long OFFSET_ID                 = 0L;
    public static final long OFFSET_PRICE              = 8L;
    public static final long OFFSET_TIMESTAMP          = 16L;
    public static final long OFFSET_TRIGGER_PRICE      = 24L;
    public static final long OFFSET_QTY                = 32L;
    public static final long OFFSET_DISPLAY_QTY        = 36L;
    public static final long OFFSET_HIDDEN_QTY         = 40L;
    public static final long OFFSET_PEAK_QTY           = 44L;
    public static final long OFFSET_PARENT_LEVEL_INDEX = 48L;
    public static final long OFFSET_NEXT_INDEX         = 52L;
    public static final long OFFSET_PARTICIPANT_ID     = 56L;
    public static final long OFFSET_SIDE               = 60L;
    public static final long OFFSET_TYPE               = 61L;
    public static final long OFFSET_STATUS             = 62L;
    public static final long OFFSET_RESERVED           = 63L;

    // Cache Line 2 (Cold)
    public static final long OFFSET_PREV_INDEX         = 64L;
    public static final long OFFSET_ORIGINAL_QTY       = 68L;

    // Arena & Segment
    private final Arena arena;
    private final MemorySegment segment;

    // Free List
    private final int[] freeList;
    private int freeTop;
    public int acquire(){
        if(freeTop==0) throw new IllegalStateException("FreeList Underflow");   
        return freeList[--freeTop];
    }
    public void release(int index){
        if(freeTop >= freeList.length) throw new IllegalStateException("FreeList Overflow");
        freeList[freeTop++] = index;
    }

    // Static methods

    private static long byteOffset(int index){
        return (long) index << ORDER_SHIFT;
    }

    // Constructors 

    public OrderPool(int capacity){
        arena = Arena.ofConfined();
        segment = arena.allocate((long) capacity * ORDER_BYTES, BYTE_ALLIGNMENT);

        freeTop = capacity;
        freeList = IntStream.rangeClosed(0, freeTop).toArray();
    }

    // public methods

    public MemorySegment segment() {
        return this.segment;
    }
    
}
