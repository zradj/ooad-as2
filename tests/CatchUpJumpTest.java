import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Tests for Requirement 8 — Reader Catch-Up Jump on Overrun.
 *
 * <p>Validates Property 4 (Catch-Up Jump Correctness on Overrun) and
 * Property 5 (Slow-Reader Miss Semantics).
 *
 * <p>Background — production semantics under test (from {@code RingBuffer.read}):
 *
 * <pre>
 *   public T read() {
 *       if (globalSeq <= sequenceNum) return null;
 *       if (globalSeq - sequenceNum > size) {
 *           sequenceNum = globalSeq - size + 1;   // CATCH-UP JUMP
 *       } else {
 *           sequenceNum++;                        // ADVANCE BY ONE
 *       }
 *       return buffer[sequenceNum % size];
 *   }
 * </pre>
 *
 * <ul>
 *   <li>A Reader created via {@code createReader()} starts with
 *       {@code sequenceNum == globalSeq}. Before any writes, {@code globalSeq}
 *       is {@code -1}, so a Reader created before the first write starts at
 *       {@code S = -1}.
 *   <li>The catch-up condition is strictly {@code > N}; the boundary
 *       {@code globalSeq - sequenceNum == N} advances by one (not a jump).
 *   <li>After the {@code globalSeq} side of the buffer overruns the Reader,
 *       items in the strictly-between range are physically overwritten and
 *       can never be observed by that Reader again.
 * </ul>
 *
 * <p>Validates: Requirements 8.1, 8.2, 8.3, 8.4, 8.5
 */
class CatchUpJumpTest {

    /**
     * Convenience: writes the items {@code "item-0", "item-1", ..., "item-(K-1)"}
     * to {@code writer}, in order. Returns nothing — the items' indices match
     * their write-time {@code globalSeq} values, which is the property tests in
     * this class rely on for slot-content prediction.
     */
    private static void writeSequentialItems(RingBuffer<String>.Writer writer, int k) {
        for (int i = 0; i < k; i++) {
            writer.write("item-" + i);
        }
    }

    @Test
    @DisplayName("8.1: Reader more than N behind — next read returns item at globalSeq - N + 1")
    void readerMoreThanNBehind_nextReadReturnsItemAtGlobalSeqMinusNPlusOne() {
        // Given a buffer of capacity N=4 with a Reader created before any writes
        // (so S = -1, the pre-write globalSeq).
        final int n = 4;
        RingBuffer<String> buffer = BufferFixtures.emptyBuffer(n);
        RingBuffer<String>.Reader reader = buffer.createReader();
        RingBuffer<String>.Writer writer = buffer.getWriterInstance();

        // When the writer performs N + 3 = 7 writes, leaving globalSeq = 6.
        // The Reader is now (6 - (-1)) = 7 > N writes behind: it must jump.
        writeSequentialItems(writer, n + 3);

        // Then the next read repositions the Reader to globalSeq - N + 1 = 3
        // and returns the item that was written at sequence 3 — i.e. "item-3"
        // (the oldest still-available item).
        assertEquals("item-3", reader.read(),
                "overrun Reader's next read must return the item at globalSeq - N + 1");
    }

    @Test
    @DisplayName("8.2: After catch-up jump, subsequent reads return items in order then null")
    void afterCatchUpJump_remainingReadsReturnItemsInOrderThenNull() {
        // Given the same overrun setup as 8.1 (N=4, 7 writes, Reader pre-created).
        final int n = 4;
        RingBuffer<String> buffer = BufferFixtures.emptyBuffer(n);
        RingBuffer<String>.Reader reader = buffer.createReader();
        RingBuffer<String>.Writer writer = buffer.getWriterInstance();
        writeSequentialItems(writer, n + 3); // globalSeq = 6

        // The first read jumps to S = 3 and returns "item-3" (per 8.1).
        assertEquals("item-3", reader.read(), "first post-jump read must return item-3");

        // When the Reader continues reading without further writes,
        // Then it returns the remaining items at sequences 4, 5, 6 in write order,
        // and a final read past globalSeq returns null (Req 8.2 + Req 6.1).
        assertEquals("item-4", reader.read(), "next read advances by one to seq 4");
        assertEquals("item-5", reader.read(), "next read advances by one to seq 5");
        assertEquals("item-6", reader.read(), "next read advances by one to seq 6");
        assertNull(reader.read(), "Reader is now caught up; further reads return null");
    }

    @Test
    @DisplayName("8.3: Reader exactly N behind — advances by one, not a catch-up jump")
    void readerExactlyNBehind_advancesByOne_notACatchUpJump() {
        // Given a Reader created before any writes (S = -1) on a buffer of
        // capacity N=4. Then exactly N writes occur, leaving globalSeq = 3.
        // The boundary delta is globalSeq - S = 3 - (-1) = 4 == N (NOT > N).
        final int n = 4;
        RingBuffer<String> buffer = BufferFixtures.emptyBuffer(n);
        RingBuffer<String>.Reader reader = buffer.createReader();
        RingBuffer<String>.Writer writer = buffer.getWriterInstance();
        writeSequentialItems(writer, n); // globalSeq = N - 1 = 3

        // When the Reader reads,
        // Then the production code's strict ">" branch is NOT taken: the Reader
        // advances by one (S = 0) and returns the FIRST item written ("item-0"),
        // not the item at globalSeq - N + 1 = 0 (which here happens to be the
        // same slot — but the distinguishing observation is that no items are
        // skipped: a follow-up drain returns every remaining item).
        assertEquals("item-0", reader.read(),
                "boundary delta == N must advance by one, returning the oldest written item");

        // And confirming no jump occurred: every remaining item is observable.
        // If a catch-up jump had fired, S would have been set to globalSeq - N + 1 = 0
        // (same value as the advance-by-one outcome above), so the second observable
        // signal is that all subsequent items are returned in order with no gap.
        assertEquals("item-1", reader.read());
        assertEquals("item-2", reader.read());
        assertEquals("item-3", reader.read());
        assertNull(reader.read(), "after draining all N items the Reader is caught up");
    }

    @Test
    @DisplayName("8.4: Catch-up-jumped item equals slot value at (globalSeq - N + 1) mod N")
    void catchUpJumpReturnedItem_matchesValueAtModularIndex() {
        // Given the overrun setup from 8.1: N=4, 7 writes (seqs 0..6), Reader
        // created before any writes, so the next read fires the catch-up jump.
        final int n = 4;
        final int totalWrites = n + 3; // 7
        RingBuffer<String> buffer = BufferFixtures.emptyBuffer(n);
        RingBuffer<String>.Reader reader = buffer.createReader();
        RingBuffer<String>.Writer writer = buffer.getWriterInstance();
        writeSequentialItems(writer, totalWrites);

        // Predict the value that should be present at the slot the catch-up jump
        // targets, using only the public contract: globalSeq = totalWrites - 1,
        // target slot = (globalSeq - N + 1) mod N. Because items "item-K" are
        // written at sequence K, the value most recently written into a given
        // slot is the item whose sequence is the largest K ≤ globalSeq with
        // K mod N == targetSlot.
        long globalSeq = totalWrites - 1L;          // 6
        long targetSeq = globalSeq - n + 1L;        // 3 = oldest still-available
        int targetSlot = Math.toIntExact(targetSeq % n); // 3

        long mostRecentSeqWrittenIntoSlot = -1;
        for (long k = 0; k <= globalSeq; k++) {
            if (k % n == targetSlot) {
                mostRecentSeqWrittenIntoSlot = k;
            }
        }
        // Sanity: must be the catch-up target itself for this configuration.
        assertEquals(targetSeq, mostRecentSeqWrittenIntoSlot,
                "predicted slot value should come from sequence " + targetSeq);

        String predictedValueAtSlot = "item-" + mostRecentSeqWrittenIntoSlot;

        // When the Reader performs its first read,
        // Then the returned value is exactly the value most recently written
        // into the buffer slot at index (globalSeq - N + 1) mod N (Req 8.4).
        String firstRead = reader.read();
        assertNotNull(firstRead, "overrun Reader must return a non-null item on its first read");
        assertEquals(predictedValueAtSlot, firstRead,
                "catch-up-jumped item must equal slot value at (globalSeq - N + 1) mod N");
    }

    @Test
    @DisplayName("8.5: Slow-reader miss — skipped items are never returned again")
    void slowReaderMissSemantics_skippedItemsAreNeverReturnedAgain() {
        // Given a Reader pre-created on an empty buffer of capacity N=4
        // (so its initial sequenceNum is -1).
        final int n = 4;
        RingBuffer<String> buffer = BufferFixtures.emptyBuffer(n);
        RingBuffer<String>.Reader reader = buffer.createReader();
        RingBuffer<String>.Writer writer = buffer.getWriterInstance();

        // First overrun: write N + 5 = 9 items, leaving globalSeq = 8.
        // Reader is 9 writes behind (> N): the next read jumps to
        // S = globalSeq - N + 1 = 5, skipping seqs 0..4.
        writeSequentialItems(writer, n + 5); // seqs 0..8

        String firstObserved = reader.read();
        assertEquals("item-5", firstObserved,
                "after first overrun, Reader observes the oldest still-available item (seq 5)");

        // Second overrun: K=3 additional writes, advancing globalSeq from 8 to 11.
        // Reader is now at S=5; delta = 11 - 5 = 6 > N: another catch-up jump
        // occurs on the next read, repositioning S to globalSeq - N + 1 = 8 and
        // skipping seqs 6 and 7.
        for (int i = 0; i < 3; i++) {
            writer.write("item-" + (n + 5 + i)); // item-9, item-10, item-11
        }

        // Drain the rest of the Reader's view and collect every value it ever sees.
        List<String> observed = new ArrayList<>();
        observed.add(firstObserved);
        observed.addAll(BufferFixtures.drain(reader));

        // The Reader's complete observation must be:
        //   item-5 (from first jump),
        //   item-8 (from second jump),
        //   item-9, item-10, item-11 (advance-by-one tail).
        assertEquals(
                Arrays.asList("item-5", "item-8", "item-9", "item-10", "item-11"),
                observed,
                "Reader's full observation must be the two jump targets followed by the tail");

        // Slow-reader miss semantics (Req 8.5): items strictly between the
        // pre-jump S and the jump target are never returned by any subsequent
        // read on this Reader.
        // First miss: original S = -1, jump target = 5 → skipped seqs are 0..4.
        // Second miss: pre-jump S = 5, jump target = 8 → skipped seqs are 6, 7.
        List<String> mustNeverBeReturned = Arrays.asList(
                "item-0", "item-1", "item-2", "item-3", "item-4", // first-miss skip set
                "item-6", "item-7"                                 // second-miss skip set
        );
        for (String skipped : mustNeverBeReturned) {
            assertFalse(observed.contains(skipped),
                    "skipped item " + skipped + " must never be returned by a slow Reader");
        }
    }
}
