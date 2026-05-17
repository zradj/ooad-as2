import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Tests for Requirement 11 — FIFO Round-Trip Property (Within Capacity).
 *
 * <p>Validates Property 3 (FIFO Within Capacity): for any {@code RingBuffer<T>}
 * of capacity {@code N}, any {@code K ≤ N}, any sequence of {@code K} non-null
 * writes, and a Reader created <em>before</em> any of those writes, the {@code K}
 * consecutive values returned by {@code read()} equal the values written, in
 * write order. The property holds across repeated round trips on the same
 * Reader so long as the Reader never falls more than {@code N} behind
 * {@code globalSeq} (i.e., no Catch_Up_Jump occurs).
 *
 * <p>Strategy:
 *
 * <ul>
 *   <li>Pre-create the Reader before any writes so its initial
 *       {@code sequenceNum == -1 == globalSeq}; this guarantees no value
 *       written within the round trip is ever skipped.</li>
 *   <li>Constrain the matrix to {@code K ≤ N} so no Capacity_Wrap can
 *       affect read order within a single round trip.</li>
 *   <li>Drain the Reader via {@link BufferFixtures#drain} after writes so the
 *       returned list is exactly the items the Reader observed, in order.</li>
 * </ul>
 *
 * <p>Validates: Requirements 11.1, 11.2
 */
class FifoRoundTripTest {

    /**
     * Validates: Requirements 11.1 (also Property 3 — FIFO Within Capacity).
     *
     * <p>For a Reader created before any writes on a buffer of capacity {@code N},
     * writing {@code K} items (with {@code K ≤ N}) and then draining the Reader
     * yields exactly the written items in write order. The matrix exercises the
     * minimum capacity ({@code N=1}), the trivial {@code K=1} case, and several
     * "fill exactly to capacity" cases ({@code K=N}) plus partial-fill cases
     * ({@code K<N}).
     */
    @ParameterizedTest(name = "N={0}, K={1}")
    @CsvSource({
            "1, 1",
            "2, 1",
            "2, 2",
            "5, 3",
            "5, 5",
            "16, 16",
            "16, 8"
    })
    void kWritesThenKReadsUnderCapacity_returnsItemsInWriteOrder(int n, int k) {
        // Given a fresh buffer of capacity N with a Reader created BEFORE any writes.
        RingBuffer<String> buffer = BufferFixtures.emptyBuffer(n);
        RingBuffer<String>.Reader reader = buffer.createReader();
        RingBuffer<String>.Writer writer = buffer.getWriterInstance();

        // When K items are written (K ≤ N, no overrun is possible for this Reader).
        List<String> written = new ArrayList<>();
        for (int i = 0; i < k; i++) {
            String item = "item-" + i;
            writer.write(item);
            written.add(item);
        }

        // Then draining the Reader returns exactly the written items in write order.
        List<String> read = BufferFixtures.drain(reader);
        assertEquals(written, read,
                "drained list must equal written list in order for K ≤ N");
        // And the Reader is caught up — no further items remain.
        assertNull(reader.read(),
                "Reader must be caught up after consuming all K writes");
    }

    /**
     * Validates: Requirements 11.2 (also Property 3 — FIFO Within Capacity).
     *
     * <p>Two sequential round trips on the same Reader produce identical read
     * sequences. With {@code N=5} and {@code K=3}:
     *
     * <ul>
     *   <li>First batch: write K items, drain. After drain, Reader's
     *       {@code sequenceNum == globalSeq == K - 1 == 2}.</li>
     *   <li>Second batch: write the same K items again. Now
     *       {@code globalSeq == 2K - 1 == 5}.</li>
     *   <li>{@code delta = globalSeq - sequenceNum = K == 3 ≤ N == 5}, so the
     *       Reader is not overrun and no Catch_Up_Jump occurs.</li>
     *   <li>The Reader reads items at sequence numbers {@code K..2K - 1} in
     *       order, which are the same item values as the first batch.</li>
     * </ul>
     */
    @Test
    @DisplayName("11.2: two sequential round trips yield identical read sequences")
    void twoSequentialRoundTrips_produceIdenticalReadSequences() {
        int n = 5;
        int k = 3;

        // Given a fresh buffer of capacity N with a Reader created BEFORE any writes.
        RingBuffer<String> buffer = BufferFixtures.emptyBuffer(n);
        RingBuffer<String>.Reader reader = buffer.createReader();
        RingBuffer<String>.Writer writer = buffer.getWriterInstance();

        // The K items written in each round trip are identical.
        List<String> batch = List.of("a", "b", "c");

        // First round trip: write K items, drain into list1.
        for (String item : batch) {
            writer.write(item);
        }
        List<String> list1 = BufferFixtures.drain(reader);
        assertEquals(batch, list1, "first round trip must read items in write order");

        // After the first drain: Reader.sequenceNum == globalSeq == K - 1 == 2.

        // Second round trip: write the same K items again, drain into list2.
        // After this batch: globalSeq == 2K - 1 == 5; delta == K == 3 ≤ N == 5,
        // so no Catch_Up_Jump occurs and the Reader reads items at sequence
        // numbers K..2K-1 in order.
        for (String item : batch) {
            writer.write(item);
        }
        List<String> list2 = BufferFixtures.drain(reader);
        assertEquals(batch, list2, "second round trip must read items in write order");

        // The two round trips produced identical read sequences (Req 11.2).
        assertEquals(list1, list2,
                "two sequential round trips with the same input must produce identical read sequences");
    }
}
