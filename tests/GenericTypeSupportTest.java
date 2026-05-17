import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Tests for Requirement 10 — Generic Type Support.
 *
 * <p>Validates Property 7 (Generic Type Preservation): for any
 * {@code RingBuffer<T>} and any non-null reference {@code t : T} written to the
 * buffer, a Reader's subsequent {@code read()} that observes {@code t} returns
 * the same reference ({@code ==}), not a copy, and the runtime type of the
 * returned value is exactly {@code T} (no widening, no narrowing).
 *
 * <p>Strategy:
 *
 * <ul>
 *   <li>For {@code Integer}, use values outside the JVM's cached range
 *       ({@code -128..127}) so that boxed values are guaranteed to be fresh
 *       references and any accidental aliasing through the integer cache
 *       cannot mask a value-vs-reference defect.
 *   <li>For {@code String}, value equality via {@code assertEquals} is
 *       sufficient per the task description ("returned values are
 *       {@code String} and equal to the written values").
 *   <li>For the custom type, declare a {@code private static record} inside
 *       this test class and assert reference identity via {@code assertSame}
 *       so the test fails if the buffer ever copies elements.
 * </ul>
 *
 * <p>Validates: Requirements 10.1, 10.2, 10.3
 */
class GenericTypeSupportTest {

    /**
     * Custom user-defined type used by Req 10.3. Declared as a {@code private
     * static record} per the task spec so the test class owns the type
     * (avoiding cross-test coupling) and the type has well-defined
     * {@code equals}, {@code hashCode}, and {@code toString}.
     */
    private static record Point(int x, int y) {}

    @Test
    @DisplayName("10.1: RingBuffer<Integer> round-trip returns Integer values equal to those written")
    void ringBufferOfInteger_writeReadRoundTrip_returnsIntegerEqualToWritten() {
        // Use values OUTSIDE the cached -128..127 Integer range so that each
        // boxed Integer is a distinct reference. This guarantees that an
        // assertEquals check is exercising actual value equality through the
        // buffer and not coincidental reference aliasing via Integer.valueOf
        // caching.
        Integer a = 200;
        Integer b = 500;
        Integer c = 1000;

        RingBuffer<Integer> buffer = BufferFixtures.emptyBuffer(4);
        RingBuffer<Integer>.Reader reader = buffer.createReader();
        RingBuffer<Integer>.Writer writer = buffer.getWriterInstance();

        writer.write(a);
        writer.write(b);
        writer.write(c);

        Integer r1 = reader.read();
        Integer r2 = reader.read();
        Integer r3 = reader.read();

        // Runtime type must be exactly Integer (no widening, no narrowing).
        assertInstanceOf(Integer.class, r1, "1st read must be an Integer");
        assertInstanceOf(Integer.class, r2, "2nd read must be an Integer");
        assertInstanceOf(Integer.class, r3, "3rd read must be an Integer");

        // Value equality is the explicit Req 10.1 contract.
        assertEquals(a, r1, "1st read must equal 1st written value");
        assertEquals(b, r2, "2nd read must equal 2nd written value");
        assertEquals(c, r3, "3rd read must equal 3rd written value");
    }

    @Test
    @DisplayName("10.2: RingBuffer<String> round-trip returns String values equal to those written")
    void ringBufferOfString_writeReadRoundTrip_returnsStringEqualToWritten() {
        String a = "alpha";
        String b = "beta";
        String c = "gamma";

        RingBuffer<String> buffer = BufferFixtures.emptyBuffer(4);
        RingBuffer<String>.Reader reader = buffer.createReader();
        RingBuffer<String>.Writer writer = buffer.getWriterInstance();

        writer.write(a);
        writer.write(b);
        writer.write(c);

        String r1 = reader.read();
        String r2 = reader.read();
        String r3 = reader.read();

        // Runtime type must be exactly String.
        assertInstanceOf(String.class, r1, "1st read must be a String");
        assertInstanceOf(String.class, r2, "2nd read must be a String");
        assertInstanceOf(String.class, r3, "3rd read must be a String");

        // Value equality is the Req 10.2 contract.
        assertEquals(a, r1, "1st read must equal 1st written value");
        assertEquals(b, r2, "2nd read must equal 2nd written value");
        assertEquals(c, r3, "3rd read must equal 3rd written value");
    }

    @Test
    @DisplayName("10.3: RingBuffer<Point> returns the SAME reference for each unchanged item (assertSame)")
    void ringBufferOfCustomType_returnsSameReferenceForUnchangedItems() {
        // Hold the original references so we can compare by identity (==).
        // Using assertSame (not assertEquals) is essential here: records have
        // value-based equals, so an assertEquals would pass even if the buffer
        // copied items. assertSame ensures the buffer does NOT copy.
        Point p1 = new Point(1, 2);
        Point p2 = new Point(3, 4);
        Point p3 = new Point(5, 6);

        RingBuffer<Point> buffer = BufferFixtures.emptyBuffer(4);
        RingBuffer<Point>.Reader reader = buffer.createReader();
        RingBuffer<Point>.Writer writer = buffer.getWriterInstance();

        writer.write(p1);
        writer.write(p2);
        writer.write(p3);

        Point r1 = reader.read();
        Point r2 = reader.read();
        Point r3 = reader.read();

        // Runtime type must be exactly Point.
        assertInstanceOf(Point.class, r1, "1st read must be a Point");
        assertInstanceOf(Point.class, r2, "2nd read must be a Point");
        assertInstanceOf(Point.class, r3, "3rd read must be a Point");

        // Reference identity — the buffer must return the very same object
        // that was written, not a copy or a reconstructed value (Req 10.3).
        assertSame(p1, r1, "1st read must return the SAME reference as written");
        assertSame(p2, r2, "2nd read must return the SAME reference as written");
        assertSame(p3, r3, "3rd read must return the SAME reference as written");
    }
}
