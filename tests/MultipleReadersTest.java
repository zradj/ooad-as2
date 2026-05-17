import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Tests for Requirement 9 — Multiple Independent Readers.
 *
 * <p>Validates Property 2 (Reader Independence / Non-destructive Read):
 * for any {@link RingBuffer} instance, any two co-existing Readers
 * {@code R_a} and {@code R_b}, and any sequence of writes, the sequence of
 * values returned by {@code R_b.read()} calls is identical regardless of
 * how many {@code read()} calls {@code R_a} has performed in between,
 * provided neither Reader has been overrun.
 *
 * <p>Production semantics under test:
 * <ul>
 *   <li>Each Reader has its own {@code sequenceNum}; reads do not affect any
 *       other Reader's state.</li>
 *   <li>{@code globalSeq} is shared: it advances on writes only.</li>
 *   <li>On overrun ({@code globalSeq - sequenceNum > size}), only the
 *       overrun Reader's {@code sequenceNum} jumps to
 *       {@code globalSeq - size + 1}; other Readers continue to advance by
 *       one per read on their own subsequent reads.</li>
 * </ul>
 *
 * <p>Lives in the default (unnamed) Java package because {@code RingBuffer}
 * itself is in the default package and named-package callers cannot import
 * default-package types.
 *
 * <p><b>Validates: Requirements 9.1, 9.2, 9.3, 9.4, 9.5</b>
 */
class MultipleReadersTest {

    // ------------------------------------------------------------------
    // Requirement 9.1 — Two Readers created at the same point: one Reader's
    // reads do not consume anything from the other Reader's view. The second
    // Reader's first read still returns the very first item the first
    // Reader observed.
    // ------------------------------------------------------------------

    @Test
    @DisplayName("9.1: Two Readers at same point — A reads K, B still sees the same first item")
    void twoReadersAtSamePoint_oneReadsK_otherStillSeesSameFirstItem() {
        // N=8 keeps us well under any overrun threshold for the 5 writes below.
        final int n = 8;
        RingBuffer<String> buffer = BufferFixtures.emptyBuffer(n);

        // Both Readers are created BEFORE any writes — both snapshot
        // globalSeq == -1, so both will see the same item-0 as their first read.
        RingBuffer<String>.Reader readerA = buffer.createReader();
        RingBuffer<String>.Reader readerB = buffer.createReader();
        RingBuffer<String>.Writer writer = buffer.getWriterInstance();

        for (int i = 0; i < 5; i++) {
            writer.write("item-" + i);
        }

        // Reader A drains K=3 items. These reads must NOT consume or alter
        // the items observable by Reader B.
        final int k = 3;
        for (int i = 0; i < k; i++) {
            assertEquals("item-" + i, readerA.read(),
                    "Reader A's read #" + (i + 1) + " must return item-" + i + " (write order)");
        }

        // Reader B's FIRST read still returns item-0 — exactly the same item
        // Reader A returned on its first read. Reader A's three reads have
        // not affected Reader B's sequenceNum at all.
        assertEquals("item-0", readerB.read(),
                "After Reader A read 3 items, Reader B's first read must still return item-0 "
                        + "(non-destructive read — Reader A's reads do not advance Reader B)");
    }

    // ------------------------------------------------------------------
    // Requirement 9.2 — Two Readers created at different points each start
    // at their own snapshot of globalSeq. Reader A (created before any
    // writes) sees item-0 first; Reader B (created after 3 writes) sees
    // item-3 first — never any item written before its createReader().
    // ------------------------------------------------------------------

    @Test
    @DisplayName("9.2: Readers at different points reflect their independent starting sequence numbers")
    void readersAtDifferentPoints_reflectIndependentStartingSequenceNumbers() {
        final int n = 8;
        RingBuffer<String> buffer = BufferFixtures.emptyBuffer(n);
        RingBuffer<String>.Writer writer = buffer.getWriterInstance();

        // Reader A is created before any writes (sequenceNum = -1).
        RingBuffer<String>.Reader readerA = buffer.createReader();

        // Three writes advance globalSeq from -1 to 2.
        writer.write("item-0");
        writer.write("item-1");
        writer.write("item-2");

        // Reader B snapshots globalSeq == 2 here. Its first non-null read
        // will therefore be the item written at sequenceNum + 1 == 3.
        RingBuffer<String>.Reader readerB = buffer.createReader();

        // Three more writes — globalSeq advances from 2 to 5.
        writer.write("item-3");
        writer.write("item-4");
        writer.write("item-5");

        // Reader A's first read returns item-0 (the first item written after
        // its creation).
        assertEquals("item-0", readerA.read(),
                "Reader A (created before any writes) must return item-0 — "
                        + "the first item written after its createReader() snapshot");

        // Reader B's first read returns item-3, NOT item-0/1/2 (which were
        // written before B was created) and NOT item-4/5.
        assertEquals("item-3", readerB.read(),
                "Reader B (created after 3 writes) must return item-3 — "
                        + "the first item written after its own createReader() snapshot");
    }

    // ------------------------------------------------------------------
    // Requirement 9.3 — One Reader Catch_Up_Jumped, the other unaffected.
    //
    // Strategy: an "eager" Reader R_e reads after each write and so stays
    // caught up. A "slow" Reader R_s never reads and so falls more than N
    // behind. When R_s finally reads, it Catch_Up_Jumps. R_e is at no point
    // overrun, and after a single subsequent write its read returns the
    // newly written item via the +1 advance — not via a jump.
    //
    // Setup (N = 4):
    //   - Loop 8 rounds: write item-i, then R_e.read(). After this loop:
    //       globalSeq = 7
    //       R_e.sequenceNum = 7 (caught up)
    //       R_s.sequenceNum = -1
    //   - R_s.read(): delta = 7 - (-1) = 8 > 4 → jumps to 7 - 4 + 1 = 4,
    //                returns item-4.
    //   - Write item-8: globalSeq = 8.
    //   - R_e.read(): delta = 8 - 7 = 1 ≤ 4 → +1 advance; returns item-8.
    //     This proves R_e was unaffected by R_s's jump and continues to
    //     advance by exactly one per read.
    // ------------------------------------------------------------------

    @Test
    @DisplayName("9.3: One Reader Catch_Up_Jumped — the other unaffected and advances by one")
    void oneReaderCatchUpJumped_otherUnaffectedAdvancesByOne() {
        final int n = 4;
        RingBuffer<String> buffer = BufferFixtures.emptyBuffer(n);

        // Both Readers are created BEFORE any writes — both snapshot
        // globalSeq == -1. They are otherwise interchangeable; "eager" and
        // "slow" are determined by whether they read after each write below.
        RingBuffer<String>.Reader eager = buffer.createReader();
        RingBuffer<String>.Reader slow = buffer.createReader();
        RingBuffer<String>.Writer writer = buffer.getWriterInstance();

        // Eight rounds of write+eagerRead. The eager Reader stays caught up.
        // The slow Reader never reads and falls 8 writes behind by the end.
        for (int i = 0; i < 8; i++) {
            writer.write("item-" + i);
            assertEquals("item-" + i, eager.read(),
                    "Eager Reader must observe each item in write order on round " + i);
        }

        // Slow Reader's first read triggers a Catch_Up_Jump:
        //   delta = 7 - (-1) = 8 > N = 4 → sequenceNum := 7 - 4 + 1 = 4.
        // The Reader returns the item at sequence 4, which is "item-4".
        assertEquals("item-4", slow.read(),
                "Slow Reader at S=-1 with globalSeq=7 (delta=8 > N=4) must Catch_Up_Jump "
                        + "to globalSeq - N + 1 = 4 and return item-4");

        // Eager Reader is caught up: globalSeq=7, eager.sequenceNum=7.
        // No new writes have occurred, so eager observes null.
        assertNull(eager.read(),
                "Eager Reader is caught up — must return null when no further writes occurred, "
                        + "regardless of the slow Reader's recent jump");

        // One more write: globalSeq advances to 8.
        writer.write("item-8");

        // For eager: delta = 8 - 7 = 1, NOT > N = 4 → +1 advance, returns item-8.
        // This is the key assertion of 9.3: the eager Reader was not affected
        // by the slow Reader's jump, and on its next read it advances by
        // exactly one (not a jump).
        assertEquals("item-8", eager.read(),
                "Eager Reader must advance by exactly one and return item-8 — "
                        + "the slow Reader's Catch_Up_Jump must not have altered eager's sequenceNum");
    }

    // ------------------------------------------------------------------
    // Requirement 9.4 — Non-destructive read: Reader B's full sequence of
    // returned values is identical regardless of how many reads Reader A
    // performs between writes.
    //
    // Method: build two parallel histories with the same write sequence on
    // independent RingBuffer instances. In History 1, Reader A is created
    // alongside Reader B and interleaves several reads with the writes.
    // In History 2, only Reader B exists. After all writes complete, drain
    // Reader B fully in both histories and compare the captured lists.
    //
    // N is chosen large enough that no Reader is overrun in either history,
    // so Property 2's "neither Reader has been overrun" precondition holds.
    // ------------------------------------------------------------------

    @Test
    @DisplayName("9.4: Reader B's output sequence is identical with vs. without Reader A's reads")
    void nonDestructiveRead_readerBSequenceIdenticalRegardlessOfReaderAReads() {
        final int n = 16; // comfortably above the 8 writes below — no overrun
        final int totalWrites = 8;

        // -------- History 1: Reader A interleaves reads with the writes --------
        RingBuffer<String> buffer1 = BufferFixtures.emptyBuffer(n);
        RingBuffer<String>.Reader readerA1 = buffer1.createReader();
        RingBuffer<String>.Reader readerB1 = buffer1.createReader();
        RingBuffer<String>.Writer writer1 = buffer1.getWriterInstance();

        for (int i = 0; i < 5; i++) {
            writer1.write("item-" + i);
        }
        // Reader A consumes 3 items.
        for (int i = 0; i < 3; i++) {
            readerA1.read();
        }
        for (int i = 5; i < totalWrites; i++) {
            writer1.write("item-" + i);
        }
        // Reader A consumes 2 more items.
        readerA1.read();
        readerA1.read();

        List<String> historyOneB = BufferFixtures.drain(readerB1);

        // -------- History 2: only Reader B exists, same write sequence --------
        RingBuffer<String> buffer2 = BufferFixtures.emptyBuffer(n);
        RingBuffer<String>.Reader readerB2 = buffer2.createReader();
        RingBuffer<String>.Writer writer2 = buffer2.getWriterInstance();

        for (int i = 0; i < totalWrites; i++) {
            writer2.write("item-" + i);
        }

        List<String> historyTwoB = BufferFixtures.drain(readerB2);

        // Reader B's outputs must be identical between the two histories.
        // (Property 2: Reader Independence / Non-destructive Read.)
        assertEquals(historyTwoB, historyOneB,
                "Reader B's full read sequence must be identical regardless of how many reads "
                        + "Reader A performed in between (non-destructive read)");

        // Sanity: both histories must have produced all 8 items in write order
        // (the precondition that B was never overrun).
        List<String> expected = new ArrayList<>(totalWrites);
        for (int i = 0; i < totalWrites; i++) {
            expected.add("item-" + i);
        }
        assertEquals(expected, historyOneB,
                "Sanity: Reader B in History 1 should observe all 8 items in write order");
    }

    // ------------------------------------------------------------------
    // Requirement 9.5 — Three Readers created at the same point, one drains
    // first; the remaining Readers must independently produce the full
    // write-order sequence on their own subsequent read() calls.
    //
    // K = 6, N = 10 (K ≤ N), so no Reader is ever overrun and each
    // Reader's drain produces exactly K items in write order.
    // ------------------------------------------------------------------

    @Test
    @DisplayName("9.5: Three Readers at same point — each independently drains the available items")
    void threeReadersAtSamePoint_eachIndependentlyDrainsAvailableItems() {
        final int n = 10;
        final int k = 6;
        RingBuffer<String> buffer = BufferFixtures.emptyBuffer(n);

        // All three Readers created BEFORE any writes — sequenceNum = -1 for each.
        RingBuffer<String>.Reader reader1 = buffer.createReader();
        RingBuffer<String>.Reader reader2 = buffer.createReader();
        RingBuffer<String>.Reader reader3 = buffer.createReader();
        RingBuffer<String>.Writer writer = buffer.getWriterInstance();

        // Build the expected sequence of K items in write order.
        List<String> expected = new ArrayList<>(k);
        for (int i = 0; i < k; i++) {
            String item = "item-" + i;
            writer.write(item);
            expected.add(item);
        }

        // Reader 1 drains all K items first.
        List<String> drained1 = BufferFixtures.drain(reader1);
        assertEquals(expected, drained1,
                "Reader 1 must observe all K items in write order on its drain");

        // Reader 2's drain still returns the full sequence — Reader 1's reads
        // did not consume anything from Reader 2's view.
        List<String> drained2 = BufferFixtures.drain(reader2);
        assertEquals(expected, drained2,
                "Reader 2 must independently observe all K items in write order — "
                        + "Reader 1's drain did not affect Reader 2");

        // And Reader 3 likewise.
        List<String> drained3 = BufferFixtures.drain(reader3);
        assertEquals(expected, drained3,
                "Reader 3 must independently observe all K items in write order — "
                        + "neither Reader 1's nor Reader 2's drains affected Reader 3");

        // After draining, each Reader is caught up and a follow-up read returns null.
        // Use assertSame(null, ...) via assertNull for clarity.
        assertNull(reader1.read(), "Reader 1 must be caught up after its drain");
        assertNull(reader2.read(), "Reader 2 must be caught up after its drain");
        assertNull(reader3.read(), "Reader 3 must be caught up after its drain");

        // The three drained lists are pairwise identical (transitive sanity).
        // assertSame is too strong — distinct ArrayList instances — but
        // structural equality must hold.
        assertEquals(drained1, drained2, "Reader 1 and Reader 2 must produce identical sequences");
        assertEquals(drained2, drained3, "Reader 2 and Reader 3 must produce identical sequences");
    }
}
