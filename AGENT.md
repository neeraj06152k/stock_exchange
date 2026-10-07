# AGENT INSTRUCTIONS: High-Performance Stock Exchange Matching Engine

This file defines the system context, constraints, architectural roadmap, and operational guidelines for AI coding assistants working in this repository.

---

## 1. System Environment & Paths

* **Host OS**: Windows 11 (PowerShell environment).
* **Active Working Directory (`perf` branch)**: `D:\MyWorkspaces\MatchingEngineWorkspace`
* **Functional Reference Worktree (`proto` branch)**: `D:\MyWorkspaces\MatchingEngineWorkspace-proto`
* **C++20 Reference Codebase**: `D:\MyWorkspaces\MatchingEngineWorkspace-proto\resources\stock_exchange-main`
* **JDK Location**: `D:\installers\Java\jdk-25.0.2+10.1-graalvm-community-openjdk`
* **Compilation Command**:

  ```powershell
  & "D:\installers\Java\jdk-25.0.2+10.1-graalvm-community-openjdk\bin\javac.exe" --enable-preview --release 25 -d bin (Get-ChildItem -Recurse -Filter *.java src | ForEach-Object FullName)
  ```

* **Execution Command**:

  ```powershell
  & "D:\installers\Java\jdk-25.0.2+10.1-graalvm-community-openjdk\bin\java.exe" --enable-preview -ea -cp bin <MainClass>
  ```

* **Constraint**: Pure JDK 25 only. No Gradle, Maven, or third-party dependencies.

---

## 2. Verified Baseline Reference (Proto Worktree)

The functional prototype in `..\MatchingEngineWorkspace-proto` is the functional source of truth. All engine logic must match its behavior:

1. **Price-Time Priority (FIFO)**: Passive resting limit orders queued in strict entry-time priority per price tick.
2. **Aggressor Spread Crossing**: Immediate execution of crossing limit/market orders with trade event generation.
3. **Iceberg Replenishment**: Display quantity depletion automatically reloads from hidden quantity and appends to the queue tail.
4. **O(1) Order Cancellation**: Intrusive doubly-linked order cancellation without collection traversals.
5. **Post-Match Stop Trigger Precedence**: Stop orders evaluated and swept strictly after aggressive order execution loops terminate.

---

## 3. Performance Architecture Directives

* **Zero-GC on Hot Path**: Eliminate runtime `new` object allocations for Orders, Levels, and Events. Use pre-allocated contiguous memory pools, primitive arrays, or modern FFM `MemorySegment` off-heap memory.
* **Modern JDK 25 Features**: Exploit Foreign Function & Memory (FFM) API, `java.lang.invoke.VarHandle` acquire/release memory fences, Vector API, and primitive bit-packing.
* **Cache-Line Padding**: Apply 128-byte cache line padding (L1 cache line + spatial prefetcher) on cross-thread synchronization cursors.
* **GraalVM Native Image**: Code must remain compatible with AOT compilation via `native-image.cmd`.

---

## 4. End-to-End Implementation Roadmap

Progress through these milestones sequentially:

1. **Memory & Domain Modeling**: Evaluate FFM `MemorySegment` vs. primitive bit-packing vs. primitive arrays for zero-GC Orders, Levels, and Symbols.
2. **Lock-Free SPSC Ring Buffer**: Circular power-of-two queue with `VarHandle` acquire/release semantics and 128-byte cursor padding.
3. **Zero-Allocation OrderBook Core**: Flat contiguous array-backed price levels and intrusive pool-allocated orders.
4. **Disruptor / Event Pipeline**: Flyweight event handling for outbound trade execution reports.
5. **Parity Verification & Latency Benchmarks**: Port test suite to verify parity with `proto`; benchmark throughput and p99/p99.9 latency.
6. **GraalVM Native Image Compilation**: Compile to a standalone Windows native executable (`.exe`).

---

## 5. Working Rules & ADR Maintenance

* **Cardinal Parity Rule**: All matching engine business logic, order state transitions, event emissions, matching priorities, and edge-case handling must strictly maintain 1:1 behavioral parity with the reference C++ codebase (`resources\stock_exchange-main`).
* **Mentor Mode**: Explain low-latency mechanics, evaluate modern JDK 25 trade-offs, and guide implementation. Avoid blind, monolithic code dumps.
* **Communication Style**: Direct, technical, and compact. Omit pleasantries and filler text.
* **Markdown Rendering Constraint**: The Notepad++ Markdown Viewer uses a lightweight CommonMark/HTML renderer that does not support LaTeX syntax (`$` or `\in`). Output all documentation, markdown files, and explanations using plain ASCII / Unicode / code formatting without LaTeX delimiters.
* **Maintain `README.md` ADR Log**: Whenever an architectural or performance decision is finalized (e.g. FFM vs primitives, ring buffer layout, memory pooling), document the rationale, trade-offs, and impact in the ADR table of `README.md`.
