# Matching Engine - Functional Prototype (`proto` branch)

## Purpose & Scope

This branch houses the **functional prototype** and **golden reference model** of the stock exchange matching engine.

* **Business Logic Exploration**: Establishes correct order book mechanics, order routing, fill algorithms, iceberg replenishment, and stop-loss triggering using idiomatic Java (standard collections, explicit object references).
* **Source of Truth**: Serves as the functional baseline and validation benchmark for the high-performance, mechanically-sympathetic implementation (zero-GC, lock-free ring buffers, primitive packing, and GraalVM AOT compilation) developed on the `main` branch.

---

## High-Level Architecture & Flow

```
                                [ Client Inbound Request ]
                                            │
                                            ▼
                             ┌─────────────────────────────┐
                             │   MatchingEngine (Router)   │
                             │   - Symbol routing          │
                             │   - Sequence assigning      │
                             └──────────────┬──────────────┘
                                            │
                                            ▼
                             ┌─────────────────────────────┐
                             │       OrderBook.java        │
                             │   - Order validation        │
                             └──────────────┬──────────────┘
                                            │
                         Action: ┌──────────┼──────────┐
                                 │          │          │
                                [ADD]   [CANCEL]   [MODIFY]
                                 │          │          │
                                 ▼          ▼          ▼
                      ┌───────────────┐  ┌──────┐  ┌──────────────┐
                      │ Type Check:   │  │ O(1) │  │ In-place qty │
                      │ Stop vs Limit │  │ Remove│ │ reduction    │
                      └───┬───────┬───┘  └──────┘  └──────────────┘
                          │       │
              Is STOP?    │       │ Immediate / Limit
           ┌──────────────┘       └──────────────┐
           ▼                                     ▼
┌──────────────────────┐              ┌──────────────────────┐
│  Stop Orders Queue   │              │   Crossing Engine    │
│  (stopBuys/stopSells)│              │  (matchAgainstLevel) │
│  - Staged until      │              └──────────┬───────────┘
│    trade price hit   │                         │
└──────────────────────┘                         ├─ Full / Partial Match
                                                 │   ├─ Emit TRADE Event
                                                 │   ├─ Maker: FILLED / PARTIAL
                                                 │   └─ Check Iceberg Reload
                                                 ▼
                                      ┌──────────────────────┐
                                      │ Remaining Quantity?  │
                                      └───┬──────────────┬───┘
                                          │ No           │ Yes
                                          ▼              ▼
                                     [ Complete ]   ┌──────────────────────┐
                                                    │   Rest on Book       │
                                                    │   (FIFO Level Queue) │
                                                    └──────────┬───────────┘
                                                               │
                                                               ▼
                                                    ┌──────────────────────┐
                                                    │ Evaluate Stop Orders │
                                                    │ (Trigger & Sweep)    │
                                                    └──────────────────────┘
```

---

## Order State Machine

```
              ┌───────────────────────────┐
              │          NEW /            │
              │     INBOUND REQUEST       │
              └─────────────┬─────────────┘
                            │
               Validation   │
               Passed       ├─────────────────────────┐ Failed
                            │                         ▼
                            ▼              ┌─────────────────────┐
                 ┌────────────────────┐    │   ORDER_REJECTED    │
                 │   ORDER_ACCEPTED   │    └─────────────────────┘
                 └──────────┬─────────┘
                            │
            ┌───────────────┴───────────────┐
            │                               │
       Stop Order                      Market / Limit
            │                               │
            ▼                               ▼
 ┌─────────────────────┐        ┌───────────────────────┐
 │   STAGED IN STOP    │        │   CROSSES SPREAD?     │
 │    HOLDING BOOK     │        └───────┬───────┬───────┘
 └──────────┬──────────┘                │       │
            │ Trade Price               │ No    │ Yes
            │ Triggered                 │       │
            └───────────────┐           │       ▼
                            ▼           │  ┌─────────────────┐
                 ┌────────────────────┐ │  │   TRADE EVENT   │
                 │   EVALUATE FOR     │ │  └────────┬────────┘
                 │     CROSSING       │ │           │
                 └──────────┬─────────┘ │           ├─ Remaining Qty = 0
                            │           │           │   ▼
                            ├───────────┘           │  ┌──────────────────┐
                            ▼                       │  │   ORDER_FILLED   │
                 ┌────────────────────┐             │  └──────────────────┘
                 │    ORDER_RESTED    │             │
                 │ (Enqueued at Level)│             ├─ Remaining Qty > 0
                 └──────────┬─────────┘             │   ▼
                            │                       │  ┌──────────────────┐
               ┌────────────┴────────────┐          │  │  ORDER_PARTIALLY_│
               │                         │          │  │     FILLED       │
               ▼                         ▼          │  └──────────────────┘
    ┌──────────────────────┐  ┌──────────────────┐  │   (Iceberg reloads)
    │    ORDER_CANCELED    │  │  ORDER_REDUCED   │  │
    │      (Cancel req)    │  │   (Modify req)   │  │
    └──────────────────────┘  └──────────────────┘  │
               ▲                         ▲          │
               └─────────────────────────┴──────────┘
```

---

## Core Mechanics

### 1. Price-Time Priority (FIFO)
* **Bids**: Sorted descending (highest buy price has priority).
* **Asks**: Sorted ascending (lowest sell price has priority).
* Each price level is an intrusive doubly-linked list (`Level.java`). Insertion is $O(1)$ at the tail; matching is $O(1)$ from the head.

```
 Price Level ($150.00)
┌─────────────────────────────────────────────────────────────┐
│ Head -> [ Order #1 (100) ] <-> [ Order #2 (200) ] <- Tail   │
│ Total Volume: 300 shares | Order Count: 2                   │
└─────────────────────────────────────────────────────────────┘
```

### 2. Iceberg Orders
* Iceberg orders divide total inventory into `displayQty` (visible on the book) and `hiddenQty`.
* When incoming aggressive volume depletes the active `displayQty`, the remaining `hiddenQty` reloads a fresh display slice (`min(hiddenQty, peakQty)`) and loses queue priority by appending to the tail of the price level.

### 3. Stop & Stop-Limit Orders
* Inbound stop orders bypass matching and stage in `stopBuys` / `stopSells` navigable maps.
* **Precedence Rule**: Stop orders are evaluated strictly **after** an inbound aggressive order finishes its entire execution sweep. Triggered stop orders are then processed sequentially against the book.

---

## Build and Verification

Run pure JDK 25 commands without external build plugins:

### 1. Compile
```powershell
& "<JDK_PATH>\bin\javac.exe" --enable-preview --release 25 -d bin (Get-ChildItem -Recurse -Filter *.java src | ForEach-Object FullName)
```

### 2. Run Test Suite
```powershell
& "<JDK_PATH>\bin\java.exe" --enable-preview -ea -cp bin dev.neeraj.exchange.MatchingEngineTest
```

