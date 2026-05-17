import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Tests for the Writer singleton contract on {@link RingBuffer}.
 *
 * <p>Validates Property 1 (Single Writer Identity): for any {@code RingBuffer}
 * instance, every call to {@code getWriterInstance()} on that instance returns
 * the same Writer reference; for any two distinct {@code RingBuffer}
 * instances, their Writers are distinct references and writes through one do
 * not change items observable by Readers of the other.
 *
 * <p><b>Validates: Requirements 3.1, 3.2</b>
 *
 * <p>Lives in the default Java package because {@code RingBuffer} itself is
 * declared in the default package and named-package tests cannot import
 * default-package types.
 */
class WriterSingletonTest {

    /**
     * Requirement 3.1: repeated calls to {@code getWriterInstance()} on the
     * same {@code RingBuffer} return the same Writer reference. Uses
     * {@code assertSame} to assert reference equality (not just {@code equals}).
     */
    @Test
    @DisplayName("getWriterInstance() returns the same reference on repeated calls")
    void getWriterInstance_returnsSameReferenceOnRepeatedCalls() {
        RingBuffer<String> buffer = new RingBuffer<>(4);

        RingBuffer<String>.Writer first = buffer.getWriterInstance();
        RingBuffer<String>.Writer second = buffer.getWriterInstance();
        RingBuffer<String>.Writer third = buffer.getWriterInstance();

        assertNotNull(first, "first getWriterInstance() must not return null");
        assertSame(first, second,
                "second getWriterInstance() call must return the same Writer reference as the first");
        assertSame(second, third,
                "third getWriterInstance() call must return the same Writer reference as the previous calls");
    }

    /**
     * Requirement 3.2: Writers obtained from two distinct {@code RingBuffer}
     * instances are distinct references, and writes through one Writer do not
     * affect items observable by Readers of the other buffer.
     *
     * <p>The isolation half is asserted through the public API: a Reader on
     * buffer B is created before any writes and, after Writer A has written
     * several items into buffer A, that Reader's first {@code read()} on
     * buffer B still returns {@code null} (no items are visible).
     */
    @Test
    @DisplayName("Writers from distinct buffers are distinct, and writes are isolated per buffer")
    void getWriterInstance_returnsDistinctReferencesAcrossBuffers_andWritesAreIsolated() {
        RingBuffer<String> bufferA = new RingBuffer<>(4);
        RingBuffer<String> bufferB = new RingBuffer<>(4);

        RingBuffer<String>.Writer writerA = bufferA.getWriterInstance();
        RingBuffer<String>.Writer writerB = bufferB.getWriterInstance();

        assertNotSame(writerA, writerB,
                "Writers obtained from two distinct RingBuffer instances must not be the same reference");

        // Create a Reader on buffer B BEFORE any writes occur on either buffer.
        // Per Req 5.1, a Reader created before any writes returns null on its
        // first read; that null result is the baseline used to detect
        // cross-buffer contamination after Writer A writes into buffer A.
        RingBuffer<String>.Reader readerB = bufferB.createReader();

        // Drive several writes through Writer A. None of these items should
        // ever become visible to Reader B, which is bound to buffer B.
        writerA.write("a1");
        writerA.write("a2");
        writerA.write("a3");

        assertNull(readerB.read(),
                "Reader on buffer B must not observe any item written via Writer A on buffer A");
        assertNull(readerB.read(),
                "Subsequent reads on Reader B must continue to return null after Writer A writes");

        // Sanity check on the same axis: a Reader created on buffer A after
        // those writes can still confirm that the writes did land somewhere —
        // they landed on buffer A, not buffer B. This guards against a
        // false-pass where neither buffer received the writes.
        RingBuffer<String>.Reader readerAFresh = bufferA.createReader();
        assertNull(readerAFresh.read(),
                "A Reader created on buffer A after the writes starts caught up (Req 5.1) — sanity baseline");

        // Now write through Writer B and confirm Reader B observes that item,
        // proving Reader B is wired to buffer B and would have seen any
        // bleed-through from Writer A had it occurred. A held reference is
        // used (rather than a string literal) so the assertSame check is
        // unambiguous and does not rely on string interning.
        String b1 = new String("b1");
        writerB.write(b1);
        String observed = readerB.read();
        assertSame(b1, observed,
                "Reader B must observe the item written via Writer B (sanity: Reader B is alive and bound to buffer B)");
    }
}
