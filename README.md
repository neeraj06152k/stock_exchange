# Ultra-Low-Latency Stock Exchange Matching Engine (`perf` branch)

A deterministic, ultra-low-latency limit order book (LOB) and matching engine implemented in modern Java 25, engineered with mechanical sympathy for sub-microsecond execution and compiled ahead-of-time (AOT) to a native binary via GraalVM.

---

## 1. System Vision & Architecture

The objective of this branch is to achieve the execution speed, cache locality, and predictability of low-latency C++ within modern Java 25.

```
                             [ Inbound Network Gateway ]
                                          │
                        ┌─────────────────▼─────────────────┐
                        │    Lock-Free SPSC Input Queue     │
                        │  (128-byte cache line padded)     │
                        └─────────────────┬─────────────────┘
                                          │ Acquire / Release
                                          ▼
┌───────────────────────────────────────────────────────────────────────────────────────────┐
│                           DEDICATED MATCHING CORE (Pinned Thread)                         │
│                                                                                           │
│   ┌───────────────────────────────────────────────────────────────────────────────────┐   │
│   │ 1. Inbound Dispatcher: O(1) Symbol routing via primitive 64-bit integer IDs       │   │
│   └─────────────────────────────────────────┬─────────────────────────────────────────┘   │
│                                             │                                             │
│                                             ▼                                             │
│   ┌───────────────────────────────────────────────────────────────────────────────────┐   │
│   │ 2. OrderBook Crossing Loop: Price-Time Priority match against resting levels      │   │
│   │    - Flat array-backed price levels                                               │   │
│   │    - Intrusive zero-allocation doubly-linked order lists                          │   │
│   │    - Direct register math for scaled fixed-point prices                           │   │
│   └─────────────────────────────────────────┬─────────────────────────────────────────┘   │
│                                             │                                             │
│                                             ▼                                             │
│   ┌───────────────────────────────────────────────────────────────────────────────────┐   │
│   │ 3. Post-Match Trigger Precedence: Sequential evaluation of armed Stop Orders      │   │
│   └─────────────────────────────────────────┬─────────────────────────────────────────┘   │
│                                             │                                             │
│                                             ▼                                             │
│   ┌───────────────────────────────────────────────────────────────────────────────────┐   │
│   │ 4. Flyweight Execution Logger: Mutates pre-allocated event ring buffer slots      │   │
│   └───────────────────────────────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────┬─────────────────────────────────────────────┘
                                              │
                        ┌─────────────────────▼─────────────┐
                        │    Lock-Free SPSC Output Queue    │
                        │   (128-byte cache line padded)    │
                        └─────────────────────┬─────────────┘
                                              │
                                              ▼
                             [ Market Data & Drop-Copy ]
```

---

## 2. Low-Latency Engineering Principles

```
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│                                   MECHANICAL SYMPATHY                                   │
├──────────────────────────┬─────────────────────────────┬────────────────────────────────┤
│       ZERO-GC PATH       │     CACHE LOCALITY          │       LOCK-FREE CONCURRENCY    │
│ - Zero runtime `new`     │ - Flat contiguous memory    │ - Single-producer single-      │
│ - Pre-allocated pools    │ - 128-byte cache padding    │   consumer (SPSC) ring buffers │
│ - Bit-packed primitives  │ - False-sharing elimination │ - Acquire/Release memory fences│
│ - Reusable flyweights    │ - Sequential memory access  │ - Single-writer core loop      │
└──────────────────────────┴─────────────────────────────┴────────────────────────────────┘
```

### 1. Zero-Allocation Critical Path (Zero GC Pauses)
* Standard Java objects incur 12–16 bytes of object header overhead, pointer indirection, and trigger Stop-The-World (STW) GC pauses.
* **Our Approach**:
  * The critical execution loop allocates **zero** heap objects.
  * All domain entities (Orders, Levels, Events) are pre-allocated at startup inside fixed-capacity contiguous pools or represented as primitive packed types.
  * Symbols are encoded as primitive 64-bit `long` registers rather than heap-allocated `java.lang.String`.

### 2. Cache Locality & False-Sharing Elimination
* Modern x86_64 CPUs access data in 64-byte cache lines, backed by hardware spatial prefetchers fetching adjacent line pairs (128 bytes).
* **Our Approach**:
  * Multi-threaded synchronization points (e.g. RingBuffer head and tail cursors) are separated by **128 bytes of padding** to guarantee they occupy independent cache lines, preventing cache-line bouncing across CPU cores.
  * Price levels and order pools reside in contiguous memory to maximize L1/L2 hardware prefetch efficiency.

### 3. Single-Writer Threading Model
* Avoid locks, mutexes, synchronized blocks, and OS context switching.
* **Our Approach**:
  * The matching engine runs on a dedicated, CPU-pinned thread.
  * Inter-thread communication between the gateway thread and matching thread is mediated by lock-free SPSC queues using Java's `VarHandle` acquire/release semantics (`setRelease` / `getAcquire`).

---

## 3. Order Processing Flow & Invariants

All engine logic strictly enforces the verified behavioral baseline established in the functional prototype (`proto` branch):

```
                        [ Inbound Order ]
                                │
                                ▼
                       { Valid Order? }
                        │            │
                    Yes │            │ No
                        ▼            ▼
               [ Check Type ]    [ ORDER_REJECTED ]
               │            │
         Limit / Mkt        │ Stop / Stop-Limit
               │            │
               ▼            ▼
       { Crossing? }    [ Staged in Stop Queue ]
        │         │              │
    Yes │         │ No           │ Trigger Price Hit
        ▼         ▼              ▼
     [ TRADE ] [ REST ] ───> { Post-Match Sweep }
```

1. **Price-Time Priority (FIFO)**: Resting liquidity is strictly matched by price priority first, then arrival time order.
2. **Iceberg Display Reload**: Depletion of visible display quantity triggers an immediate reload from hidden inventory, losing queue time priority by appending to the tail of the level.
3. **Stop Precedence**: Stop orders triggered by market execution are staged and swept sequentially **after** an inbound aggressive order finishes sweeping available depth.
4. **$O(1)$ Order Cancellation**: Intrusive doubly-linked pointers enable immediate node removal from anywhere in the book without traversal.

---

## 4. Architectural & Performance Decision Records (ADR)

This section records key design decisions, trade-offs evaluated, modern JDK APIs investigated, and mechanical rationale.

| Decision ID | Component | Decision Taken | Rationale & Alternatives Evaluated | Performance Impact |
| :--- | :--- | :--- | :--- | :--- |
| **ADR-001** | **Domain Primitives** | *Under Evaluation* | Foreign Function & Memory (FFM) API (`MemorySegment`) vs. Primitive Bit-Packing (`long`) | Register utilization, cache line footprint, and memory dereferencing overhead. |
| **ADR-002** | **Inter-Thread Transport**| *Under Evaluation* | Lock-free SPSC circular ring buffer with 128-byte cache-line padding vs. `Disruptor` pattern | Cache line isolation, lock-free acquire/release latency. |
| **ADR-003** | **Order Book Storage** | *Under Evaluation* | Contiguous indexed arrays vs. intrusive object pools | L1 data cache hit rate, pointer chasing avoidance. |

---

## 5. Technology Stack & Toolchain

* **Language**: Java 25 (OpenJDK / GraalVM CE)
* **Compiler Flags**: `--enable-preview --release 25`
* **Compilation Target**: GraalVM Native Image (`native-image.cmd`) for ahead-of-time compilation to a standalone native Windows executable.
* **Dependencies**: **Zero**. Implemented entirely with pure Java 25 standard APIs.
