import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Constructor and capacity contract tests for {@link RingBuffer} (Requirement 2).
 *
 * <p>Lives in the default (unnamed) Java package because {@code RingBuffer}
 * is in the default package and named-package callers cannot import
 * default-package types.
 *
 * <p>Validates Property 8 (Empty / Caught-Up Read Returns Null) for the
 * empty-buffer case via {@link #freshReader_returnsNullOnFirstRead()}: a
 * Reader created on a freshly constructed buffer returns {@code null} on its
 * first {@code read()} call because no writes have occurred yet
 * ({@code globalSeq == -1}, Reader's {@code sequenceNum == -1}).
 *
 * <p><b>Validates: Requirements 2.1, 2.2, 2.3, 2.4, 2.5</b>
 */
class RingBufferConstructorTest {

    // ------------------------------------------------------------------
    // Requirement 2.1 — getSize() returns the constructor argument
    // ------------------------------------------------------------------

    @ParameterizedTest(name = "size={0}")
    @ValueSource(ints = {1, 2, 5, 16, 1024})
    @DisplayName("getSize() returns the size passed to the constructor for positive sizes")
    void getSize_returnsConstructorArgument_forPositiveSizes(int size) {
        RingBuffer<Object> buffer = new RingBuffer<>(size);

        assertEquals(size, buffer.getSize(),
                "getSize() must return the exact value passed to the constructor");
    }

    // ------------------------------------------------------------------
    // Requirement 2.2 — size 1 is a legal capacity
    // ------------------------------------------------------------------

    @Test
    @DisplayName("size=1: getSize()==1 and one write/read round-trip succeeds without throwing")
    void getSize_returnsOne_andWriteReadSucceedForSizeOne() {
        RingBuffer<String> buffer = new RingBuffer<>(1);

        assertEquals(1, buffer.getSize(), "getSize() must return 1 for size-1 buffer");

        // Reader created before the write so it can observe it (sequenceNum starts at globalSeq == -1).
        RingBuffer<String>.Reader reader = buffer.createReader();
        RingBuffer<String>.Writer writer = buffer.getWriterInstance();

        // Neither call should throw on a size-1 buffer.
        writer.write("only-item");
        String observed = reader.read();

        assertSame("only-item", observed,
                "size-1 buffer: a single write must be observable by a pre-created Reader");
    }

    // ------------------------------------------------------------------
    // Requirement 2.3 — fresh Reader on empty buffer returns null on first read
    // (also validates Property 8 for the empty-buffer case)
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Reader created on a freshly constructed buffer returns null on its first read()")
    void freshReader_returnsNullOnFirstRead() {
        RingBuffer<Object> buffer = new RingBuffer<>(8);

        RingBuffer<Object>.Reader reader = buffer.createReader();

        assertNull(reader.read(),
                "first read() on a fresh Reader of an empty buffer must return null "
                        + "(no writes have occurred, so globalSeq <= sequenceNum)");
    }

    // ------------------------------------------------------------------
    // Requirement 2.4 — size 0 defers the failure to the first write,
    // which throws ArithmeticException via Math.toIntExact(0 % 0).
    // (Suspected defect; recorded in tests/UNTESTABLE_ISSUES.md.)
    // ------------------------------------------------------------------

    @Test
    @DisplayName("size=0: first Writer.write(...) throws ArithmeticException (modulo-zero indexing)")
    void sizeZero_writeThrowsArithmeticException() {
        // Constructing a size-0 buffer must NOT throw — the Object[0] allocation is legal.
        RingBuffer<String> buffer = new RingBuffer<>(0);
        RingBuffer<String>.Writer writer = buffer.getWriterInstance();

        // The first write triggers computeIndex(globalSeq) -> Math.toIntExact(0 % 0),
        // which throws ArithmeticException ("/ by zero").
        assertThrows(ArithmeticException.class,
                () -> writer.write("any"),
                "size=0 buffer: the first Writer.write(...) must throw ArithmeticException "
                        + "because computeIndex performs (globalSeq % size) with size==0");
    }

    // ------------------------------------------------------------------
    // Requirement 2.5 — negative sizes are rejected by the constructor with
    // exactly NegativeArraySizeException. assertThrows(...) asserts the
    // exact class, so a subclass or any other exception type fails the test.
    // ------------------------------------------------------------------

    @ParameterizedTest(name = "size={0}")
    @ValueSource(ints = {-1, -5, Integer.MIN_VALUE})
    @DisplayName("negative size: constructor throws exactly NegativeArraySizeException")
    void negativeSize_constructorThrowsNegativeArraySizeException(int negativeSize) {
        assertThrows(NegativeArraySizeException.class,
                () -> new RingBuffer<>(negativeSize),
                "RingBuffer(" + negativeSize + ") must throw NegativeArraySizeException; "
                        + "any other exception type (or no exception) fails this test");
    }
}
