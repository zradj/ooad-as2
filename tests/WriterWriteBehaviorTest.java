import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Validates Writer write behavior (Req 4.1 – 4.4).
 *
 * <p>Asserted Properties:
 * <ul>
 *     <li>Property 3 (FIFO Within Capacity)</li>
 *     <li>Property 5 (Slow-Reader Miss Semantics)</li>
 * </ul>
 *
 * <p>All tests construct fresh {@link RingBuffer} instances via
 * {@link BufferFixtures} and never share state across tests.
 */
class WriterWriteBehaviorTest {

    /**
     * Validates: Requirements 4.1
     */
    @Test
    void write_withNullArgument_throwsNullPointerExceptionWithExpectedMessage() {
        RingBuffer<String> buffer = BufferFixtures.emptyBuffer(4);
        RingBuffer<String>.Writer writer = buffer.getWriterInstance();

        NullPointerException ex = assertThrows(
                NullPointerException.class,
                () -> writer.write(null)
        );
        assertEquals("Null values are not supported", ex.getMessage());
    }

    /**
     * Validates: Requirements 4.2
     */
    @Test
    void singleWrite_isObservedByPreCreatedReader() {
        RingBuffer<String> buffer = BufferFixtures.emptyBuffer(4);
        RingBuffer<String>.Reader reader = buffer.createReader();
        RingBuffer<String>.Writer writer = buffer.getWriterInstance();

        writer.write("only");

        assertEquals("only", reader.read());
        assertNull(reader.read(), "Reader should be caught up after consuming the single write");
    }

    /**
     * Validates: Requirements 4.3 (also Property 3 — FIFO Within Capacity).
     *
     * <p>For a Reader created before any writes on a buffer of capacity N, K
     * consecutive {@code read()} calls return the K written items in write order
     * for any K ≤ N.
     */
    @ParameterizedTest
    @CsvSource({
            "5, 1",
            "5, 3",
            "5, 5",
            "16, 10"
    })
    void kWritesUnderCapacity_areReadInWriteOrder(int n, int k) {
        RingBuffer<String> buffer = BufferFixtures.emptyBuffer(n);
        RingBuffer<String>.Reader reader = buffer.createReader();
        RingBuffer<String>.Writer writer = buffer.getWriterInstance();

        List<String> expected = new ArrayList<>();
        for (int i = 0; i < k; i++) {
            String item = "item-" + i;
            writer.write(item);
            expected.add(item);
        }

        List<String> actual = new ArrayList<>();
        for (int i = 0; i < k; i++) {
            actual.add(reader.read());
        }

        assertEquals(expected, actual,
                "K consecutive reads should return K written items in write order");
        assertNull(reader.read(),
                "Reader should be caught up after exactly K reads when K writes were made");
    }

    /**
     * Validates: Requirements 4.4 (also Property 5 — Slow-Reader Miss Semantics).
     *
     * <p>Pre-create a Reader on an empty buffer of capacity N. Drain the first
     * batch of N writes (Reader's {@code sequenceNum} now equals {@code globalSeq}).
     * Overrun the buffer by writing more than N additional items, which makes
     * the Reader fall more than N behind. The next {@code read()} triggers a
     * Catch_Up_Jump to {@code globalSeq - N + 1}, after which the Reader
     * returns the latest N items in write order, then {@code null}.
     *
     * <p>With {@code N = 4} and an extra batch of {@code N + 5 = 9} writes:
     * <ul>
     *     <li>Total writes = 13, final {@code globalSeq = 12}</li>
     *     <li>Reader was at {@code sequenceNum = 3} after the first drain</li>
     *     <li>{@code globalSeq - sequenceNum = 9 > N = 4} → Catch_Up_Jump</li>
     *     <li>New {@code sequenceNum = 12 - 4 + 1 = 9}; returns "item-9"</li>
     *     <li>Subsequent reads return "item-10", "item-11", "item-12" then {@code null}</li>
     * </ul>
     */
    @Test
    void writesBeyondCapacity_returnLatestNItemsToFreshlyOverrunReader() {
        int n = 4;
        RingBuffer<String> buffer = BufferFixtures.emptyBuffer(n);
        RingBuffer<String>.Reader reader = buffer.createReader();
        RingBuffer<String>.Writer writer = buffer.getWriterInstance();

        // First batch: fill the buffer to exactly capacity, then drain.
        for (int i = 0; i < n; i++) {
            writer.write("item-" + i);
        }
        List<String> firstDrain = BufferFixtures.drain(reader);
        assertEquals(
                List.of("item-0", "item-1", "item-2", "item-3"),
                firstDrain,
                "Reader should observe the first N writes in order before being overrun"
        );

        // Reader is now caught up: sequenceNum == globalSeq == n - 1 == 3.
        // Overrun: write n + 5 = 9 more items so the Reader falls more than N behind.
        int extra = n + 5;
        int totalWrites = n + extra; // 13 total; final globalSeq = 12
        for (int i = n; i < totalWrites; i++) {
            writer.write("item-" + i);
        }

        // After Catch_Up_Jump the Reader returns the latest N items in write order.
        // Latest N at globalSeq = 12 are items at sequence numbers 9, 10, 11, 12.
        List<String> latest = BufferFixtures.drain(reader);
        assertEquals(
                List.of("item-9", "item-10", "item-11", "item-12"),
                latest,
                "After Catch_Up_Jump, Reader should return the latest N items in write order"
        );
    }
}
