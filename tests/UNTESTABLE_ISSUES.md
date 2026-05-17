# Untestable / Observed Issues

This document records behaviors of `RingBuffer<T>` that cannot be exercised through the public API alone, appear to be defects rather than documented contract, or are explicitly out of scope. Each entry states the behavior, the reason it cannot be (or was not) tested, and a suggested non-invasive remediation.

Entries are cross-referenced from the test classes that touch the boundary of each issue. This document is part of the pull-request deliverable per Requirement 12.

---

## 1. Size-Zero `ArithmeticException` on First Write (Suspected Defect)

**Behavior:** Constructing `new RingBuffer<>(0)` succeeds without throwing. The first call to `Writer.write(item)` throws `ArithmeticException` caused by a modulo-zero operation (`Math.toIntExact(globalSeq % 0)` or equivalent) inside the write path.

**Reason:** The constructor does not validate that the supplied capacity is positive. The exception is thrown lazily on the first write rather than eagerly at construction time, which is surprising to callers. The test `RingBufferConstructorTest.sizeZero_writeThrowsArithmeticException` documents and asserts this behavior, but the behavior itself is judged a defect: a zero-capacity ring buffer is semantically meaningless and the failure mode (a cryptic `ArithmeticException` on write rather than an `IllegalArgumentException` on construction) is not described in the README.

**Suggested remediation:** Add a precondition check at the top of the `RingBuffer` constructor:
```java
if (n <= 0) throw new IllegalArgumentException("Capacity must be positive, got: " + n);
```
This is a one-line change that replaces the silent construction + deferred crash with a clear, early failure. If this change is made, update `BASELINE_RINGBUFFER_SHA256` in `OoStructuralContractTest` and remove the `sizeZero_writeThrowsArithmeticException` test (or update it to assert `IllegalArgumentException`).

---

## 2. Negative-Size `NegativeArraySizeException` (Suspected Defect)

**Behavior:** Constructing `new RingBuffer<>(-1)` (or any negative value) throws `NegativeArraySizeException` from the JVM's array-allocation machinery inside the constructor. The test `RingBufferConstructorTest.negativeSize_constructorThrowsNegativeArraySizeException` asserts this exact exception type.

**Reason:** The exception is a low-level JVM artifact, not a deliberate API contract. Callers receive no useful message and cannot distinguish a programming error (passing `-1`) from an overflow bug (passing a computed value that wrapped negative). The README does not document this behavior.

**Suggested remediation:** Same precondition check as Issue 1 above — a single `if (n <= 0)` guard at the top of the constructor covers both the zero and negative cases with a clear `IllegalArgumentException`. If this change is made, update `BASELINE_RINGBUFFER_SHA256` in `OoStructuralContractTest` and update the corresponding test.

---

## 3. Boundary Case `globalSeq − S == N` — README Ambiguity

**Behavior:** When a Reader's sequence number `S` satisfies `globalSeq − S == N` exactly (the Reader is precisely `N` writes behind), the current implementation advances the Reader by one (normal read), rather than triggering a Catch-Up Jump. The test `CatchUpJumpTest.readerExactlyNBehind_advancesByOne_notACatchUpJump` asserts this observed behavior.

**Reason:** The README describes the Catch-Up Jump condition as "a Reader more than `N` writes behind `globalSeq`", which implies the jump fires only when `globalSeq − S > N`. The boundary `globalSeq − S == N` is not explicitly addressed. The observed behavior (advance by one, no jump) is consistent with a strict `>` comparison, but the README's phrasing is ambiguous enough that a future maintainer might reasonably change the comparison to `>=`, altering the boundary semantics silently.

**Suggested remediation:** Clarify the README to state explicitly whether the Catch-Up Jump fires at `globalSeq − S > N` (strict) or `globalSeq − S >= N` (non-strict). No source change is required if the current strict-greater-than behavior is intentional; only documentation needs updating.

---

## 4. `globalSeq` Overflow at `Long.MAX_VALUE`

**Behavior:** `globalSeq` is a `long` that increments by one on every write. After `Long.MAX_VALUE` writes (`2^63 − 1 ≈ 9.2 × 10^18`), the next increment wraps to `Long.MIN_VALUE`, causing undefined behavior in the sequence-number arithmetic used by Readers.

**Reason:** Exercising this condition requires more than `2^63` write calls, which is not feasible in a unit test. The behavior is therefore not asserted by any test in this suite.

**Suggested remediation:** Document the overflow limit in the `RingBuffer` Javadoc or README as a known constraint. If overflow resilience is required, replace the `long` counter with a `BigInteger` or add a modular-arithmetic scheme that keeps `globalSeq` bounded. No change is needed for the current assignment scope.

---

## 5. Thread-Safety Out of Scope

**Behavior:** `RingBuffer` performs no synchronization. Concurrent writes or concurrent reads from multiple threads may produce data races, torn reads, or stale visibility of `globalSeq` and the internal array.

**Reason:** The README explicitly states that the implementation is not thread-safe. Thread-safety testing is therefore out of scope per Requirement 12.5. No concurrency tests are included in this suite.

**Suggested remediation:** If thread-safety is required in a future version, the internal fields (`globalSeq`, the backing array) should be protected with `synchronized` blocks, `volatile` declarations, or `java.util.concurrent.atomic` types. A separate concurrency test suite (e.g., using `java.util.concurrent.CountDownLatch` or a stress-test harness) would be needed.

---

## 6. `printBuffer()` Returns `void` — Tested via Captured Stdout

**Behavior:** `printBuffer()` writes the internal array's `Arrays.toString(...)` representation to `System.out` and returns `void`. There is no return value to assert against directly.

**Reason:** Because the method has no return value, the test suite captures `System.out` using the `StdoutCapture` utility (`tests/StdoutCapture.java`) and asserts on the captured string. This is a valid testing approach but is more fragile than asserting a return value: it depends on the method writing to `System.out` (not `System.err` or a logger), and it is sensitive to any additional output produced by the JVM or other code running concurrently during the capture window.

**Suggested remediation:** Add a package-private or protected overload `String printBuffer(PrintStream out)` (or `String formatBuffer()`) that returns the formatted string. The existing `printBuffer()` can delegate to it. This makes the output directly assertable without stdout capture and removes the fragility of stream redirection.

---

## 7. Production-Source Guard Is Hash-Based, Not a True Diff Guard

**Behavior:** The test `OoStructuralContractTest.productionSourceFiles_unmodifiedSinceBaseline` computes SHA-256 digests of `src/RingBuffer.java` and `src/Main.java` at test runtime and compares them to hard-coded baseline constants (`BASELINE_RINGBUFFER_SHA256`, `BASELINE_MAIN_SHA256`). If the files have been modified, the test fails.

**Reason:** A unit test cannot truly detect "the production source was edited" before the test runs — by the time the test executes, any change is already compiled into the working copy. The hash guard is a best-effort tripwire: it catches byte-level changes (including whitespace and comment edits) that do not alter the public API surface, but it cannot prevent a contributor from updating the baseline digest constant alongside a source change. The structural assertions in `OoStructuralContractTest` (15.1–15.8) remain the primary enforcement mechanism for API-surface changes.

**Suggested remediation:** Enforce the "no production-source modification" rule at the pull-request review level (PR diff inspection) rather than relying solely on the hash test. The hash test is a useful secondary signal. If a legitimate source change is ever needed, the contributor must update `BASELINE_RINGBUFFER_SHA256` (and/or `BASELINE_MAIN_SHA256`) in `OoStructuralContractTest` as part of the same commit and record the change in this document per Req 15.9.

---

## 8. `RingBuffer.Writer` Has a Public Synthesized Default Constructor (Req 15.4 Violation)

**Status: Known failing test** — `OoStructuralContractTest.writerHasNoPublicConstructor` fails on the current production code. This is intentional per the spec's "document, don't patch" policy (Req 14.1).

**Behavior:** `RingBuffer.Writer` declares no explicit constructor. The Java compiler therefore synthesizes a public default constructor (`public Writer()`). This violates the OO structural contract (Req 15.4), which requires that no constructor of `Writer` be public — instances should be obtainable only through `RingBuffer.getWriterInstance()`.

**Reason:** The test `OoStructuralContractTest.writerHasNoPublicConstructor` correctly detects this synthesized constructor and fails. The production source (`src/RingBuffer.java`) was not modified per Req 14.1, so the defect remains in place and the test failure is the intended signal to a reviewer.

**Suggested remediation:** Add an explicit private no-arg constructor to the `Writer` inner class in `src/RingBuffer.java`:
```java
private Writer() {}
```
This is a one-line fix that suppresses the synthesized public constructor and aligns the implementation with the documented contract. If this change is made:
1. Update `BASELINE_RINGBUFFER_SHA256` in `OoStructuralContractTest` to the new digest of the modified `src/RingBuffer.java`.
2. Record the change in this document (update this entry to "Resolved").
3. Verify that `OoStructuralContractTest.writerHasNoPublicConstructor` now passes.
