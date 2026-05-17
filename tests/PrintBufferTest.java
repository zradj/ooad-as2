import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for Requirement 13 — {@code printBuffer()} Output.
 *
 * <p>{@code RingBuffer.printBuffer()} writes
 * {@code Arrays.toString(this.buffer)} followed by a newline to
 * {@code System.out}. The internal array is laid out at modular index
 * {@code i % N}, so after {@code K} writes with {@code K > N} the array
 * contains the latest {@code N} items in <em>slot order</em>, which is not
 * necessarily write order.
 *
 * <p>All assertions are performed on text captured via {@link StdoutCapture}.
 * Captured text has line endings normalized to {@code "\n"}; the trailing
 * newline emitted by {@code println} is stripped before equality comparisons
 * so the assertion is independent of the host line separator.
 *
 * <p>13.2 deliberately checks only that each written item's {@code toString()}
 * appears as a substring of the printed line. It does not tokenize on
 * delimiters or assume any particular spacing — Req 13.2 explicitly forbids
 * mandating a {@code toString()} contract on stored items, so we use
 * {@code String} items whose {@code toString()} is the literal string itself.
 *
 * <p>Validates: Requirements 13.1, 13.2, 13.3
 */
class PrintBufferTest {

    /**
     * Strips at most one trailing {@code "\n"} from {@code captured}. Captured
     * text from {@link StdoutCapture} already has CRLF normalized to LF, so
     * a single {@code "\n"} strip is sufficient to drop the {@code println}
     * line terminator.
     */
    private static String stripTrailingNewline(String captured) {
        if (captured.endsWith("\n")) {
            return captured.substring(0, captured.length() - 1);
        }
        return captured;
    }

    @ParameterizedTest(name = "N={0}: printBuffer on a fresh buffer prints Arrays.toString of N nulls")
    @ValueSource(ints = {1, 2, 5, 16})
    @DisplayName("13.1: Fresh buffer prints Arrays.toString(new Object[N])")
    void freshBuffer_printBufferEqualsArraysToStringOfNNulls(int n) {
        // Given a freshly constructed RingBuffer of capacity N (no writes).
        RingBuffer<String> buffer = BufferFixtures.emptyBuffer(n);

        // When printBuffer() is invoked under captured stdout,
        String captured = StdoutCapture.capture(buffer::printBuffer);

        // Then the printed line (after stripping the println newline) equals
        // Arrays.toString applied to an Object[] of length N — i.e., the
        // canonical "[null, null, ..., null]" form with N nulls (Req 13.1).
        String expected = Arrays.toString(new Object[n]);
        assertEquals(expected, stripTrailingNewline(captured),
                "fresh buffer of capacity " + n
                        + " must print exactly Arrays.toString(new Object[" + n + "])");
    }

    @Test
    @DisplayName("13.2: K writes under capacity — printed line contains every item's toString()")
    void kWritesUnderCapacity_printedLineContainsAllKItemToStrings() {
        // Given a buffer of capacity N=5 with K=3 unambiguous String items.
        // Strings whose toString() returns the string literally, with no
        // bracket/comma characters, so substring checks are unambiguous and
        // do not collide with the array delimiters Arrays.toString emits.
        final int n = 5;
        String[] items = {"alpha", "bravo", "charlie"};

        RingBuffer<String> buffer = BufferFixtures.bufferWith(n, items);

        // When printBuffer() is invoked under captured stdout,
        String output = StdoutCapture.capture(buffer::printBuffer);

        // Then for every written item, the captured output contains the item's
        // toString() form (Req 13.2). containsAll semantics: every expected
        // token is present; we do NOT mandate delimiters or layout.
        for (String item : items) {
            assertTrue(output.contains(item.toString()),
                    "captured output must contain toString() of written item '"
                            + item + "'; output was: " + output);
        }
    }

    @Test
    @DisplayName("13.3: K = N + 5 — printed line contains the latest N items, not the overwritten ones")
    void kWritesAtCapacity_printedLineContainsLatestNItems() {
        // Given a buffer of capacity N=4 overrun by K=9 writes (N + 5).
        // After 9 writes at slot i % 4 the array holds the latest N items:
        //   slot 0: item-8 (overwrote item-4 which overwrote item-0)
        //   slot 1: item-5 (overwrote item-1)
        //   slot 2: item-6 (overwrote item-2)
        //   slot 3: item-7 (overwrote item-3)
        // So the latest N items are item-5..item-8; item-0..item-4 are gone.
        final int n = 4;
        final int k = n + 5; // 9

        RingBuffer<String> buffer = BufferFixtures.emptyBuffer(n);
        RingBuffer<String>.Writer writer = buffer.getWriterInstance();
        for (int i = 0; i < k; i++) {
            writer.write("item-" + i);
        }

        // When printBuffer() is invoked under captured stdout,
        String output = StdoutCapture.capture(buffer::printBuffer);

        // Then the latest N=4 items are present in the printed line (Req 13.3).
        for (int i = k - n; i < k; i++) {
            String latest = "item-" + i;
            assertTrue(output.contains(latest),
                    "captured output must contain latest item '" + latest
                            + "'; output was: " + output);
        }

        // And the overwritten items (item-0..item-4) must NOT appear.
        // None of "item-0".."item-4" are substrings of any retained
        // "item-5".."item-8", so absence checks are unambiguous at single
        // decimal digits.
        for (int i = 0; i < k - n; i++) {
            String overwritten = "item-" + i;
            assertFalse(output.contains(overwritten),
                    "captured output must NOT contain overwritten item '"
                            + overwritten + "'; output was: " + output);
        }
    }

    @Test
    @DisplayName("13.3: K = N + 1 — one write past capacity wraps the oldest slot immediately")
    void oneWritePastCapacity_printedLineReflectsImmediateWrap() {
        // Given a buffer of capacity N=4 overrun by exactly one write (K=N+1=5).
        // After 5 writes at slot i % 4 the array holds:
        //   slot 0: item-4 (overwrote item-0)  ← wrap takes effect on the very
        //                                         first write past capacity
        //   slot 1: item-1
        //   slot 2: item-2
        //   slot 3: item-3
        // So latest N items are item-1..item-4; item-0 is gone.
        final int n = 4;
        final int k = n + 1; // 5

        RingBuffer<String> buffer = BufferFixtures.emptyBuffer(n);
        RingBuffer<String>.Writer writer = buffer.getWriterInstance();
        for (int i = 0; i < k; i++) {
            writer.write("item-" + i);
        }

        // When printBuffer() is invoked under captured stdout,
        String output = StdoutCapture.capture(buffer::printBuffer);

        // Then the latest N items (item-1..item-4) are present (Req 13.3).
        for (int i = k - n; i < k; i++) {
            String latest = "item-" + i;
            assertTrue(output.contains(latest),
                    "captured output must contain latest item '" + latest
                            + "'; output was: " + output);
        }

        // And item-0 (the only overwritten item) is gone — confirming the
        // wrap-around takes effect on the first write past capacity, not on
        // some later write (Req 13.3 dedicated K=N+1 case).
        assertFalse(output.contains("item-0"),
                "captured output must NOT contain overwritten item 'item-0' "
                        + "after exactly one write past capacity; output was: " + output);
    }
}
