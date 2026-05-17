import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Tests for Requirement 7 — Capacity Wrap Without Overrun.
 *
 * <p>Validates Property 6 (Modular Index Correctness): for any
 * {@code RingBuffer} of capacity {@code N} and any sequence of {@code 2N}
 * consecutive writes paired with reads, no read or write throws
 * {@code IndexOutOfBoundsException} or {@code ArithmeticException}, and the
 * Reader observes items in write order across the index wrap from {@code N-1}
 * back to {@code 0}.
 *
 * <p>Strategy: an "eager" Reader is created before any writes, so it stays one
 * step caught up at every round. Because the Reader is always exactly one
 * write behind {@code globalSeq}, no Catch_Up_Jump occurs — only the natural
 * single-step advance through the modular index, which forces the index to
 * wrap from {@code N-1} to {@code 0} between rounds {@code N-1} and {@code N}.
 *
 * <p>Validates: Requirements 7.1, 7.2
 */
class CapacityWrapTest {

    @ParameterizedTest(name = "N={0}: 2*N interleaved write+read rounds preserve write order")
    @ValueSource(ints = {1, 2, 5, 16})
    @DisplayName("7.1: Two full cycles of writes are read in order by an eager Reader")
    void twoFullCyclesOfWrites_areReadInOrderByEagerReader(int n) {
        // Given a fresh buffer of capacity N and a Reader created BEFORE any
        // writes (so its initial sequenceNum equals globalSeq == -1).
        RingBuffer<String> buffer = BufferFixtures.emptyBuffer(n);
        RingBuffer<String>.Reader reader = buffer.createReader();
        RingBuffer<String>.Writer writer = buffer.getWriterInstance();

        // When 2*N interleaved write-then-read rounds occur, the Reader stays
        // exactly one write behind globalSeq at every step, so no Catch_Up_Jump
        // is ever triggered. The internal index naturally wraps from N-1 back
        // to 0 between rounds N-1 and N.
        int rounds = 2 * n;
        for (int i = 0; i < rounds; i++) {
            String item = "item-" + i;
            writer.write(item);

            // Then the Reader returns exactly the item just written, in write
            // order, across the Capacity_Wrap (Req 7.1).
            assertEquals(item, reader.read(),
                    "round " + i + ": Reader must observe item-" + i + " in write order");
        }
    }

    @org.junit.jupiter.api.Test
    @DisplayName("7.2: Crossing the index boundary from N-1 to 0 throws no index/arithmetic error")
    void writeAcrossIndexBoundary_doesNotThrow() {
        // Use a fixed, small N so the boundary crossing is exercised without
        // ambiguity. With N=4 the index sequence is 0, 1, 2, 3, 0, 1, 2, 3
        // across 2*N rounds — a clean wrap from N-1 (index 3) back to 0.
        final int n = 4;

        RingBuffer<String> buffer = BufferFixtures.emptyBuffer(n);
        RingBuffer<String>.Reader reader = buffer.createReader();
        RingBuffer<String>.Writer writer = buffer.getWriterInstance();

        // assertDoesNotThrow asserts no Throwable escapes the action; we add
        // explicit checks for IndexOutOfBoundsException and ArithmeticException
        // (the two failure modes named in Req 7.2) so the failure message is
        // pinpoint, while still allowing any other unexpected exception to
        // propagate as a generic test failure.
        assertDoesNotThrow(() -> {
            int rounds = 2 * n;
            for (int i = 0; i < rounds; i++) {
                String item = "item-" + i;
                try {
                    writer.write(item);
                } catch (IndexOutOfBoundsException | ArithmeticException ex) {
                    fail("write at round " + i + " threw "
                            + ex.getClass().getSimpleName() + " during index wrap", ex);
                }
                try {
                    // Drive a read every round so the Reader also crosses the
                    // boundary (its modular index is computed inside read()).
                    reader.read();
                } catch (IndexOutOfBoundsException | ArithmeticException ex) {
                    fail("read at round " + i + " threw "
                            + ex.getClass().getSimpleName() + " during index wrap", ex);
                }
            }
        }, "no exception must escape the 2*N interleaved write+read sequence");
    }
}
