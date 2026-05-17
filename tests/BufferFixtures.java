import java.util.ArrayList;
import java.util.List;

/**
 * Typed fixture builders for {@link RingBuffer} tests.
 *
 * <p>This helper reduces boilerplate when constructing the Subject_Under_Test
 * and draining a Reader. It contains no assertions of its own — it only
 * produces buffers and reads sequences.
 *
 * <p>Lives in the default (unnamed) Java package because {@code RingBuffer}
 * itself is in the default package and named packages cannot import
 * default-package types.
 */
final class BufferFixtures {

    private BufferFixtures() {
        // utility class — not instantiable
    }

    /**
     * Returns a fresh, empty {@code RingBuffer<T>} of capacity {@code n}.
     */
    static <T> RingBuffer<T> emptyBuffer(int n) {
        return new RingBuffer<>(n);
    }

    /**
     * Constructs a {@code RingBuffer<T>} of capacity {@code n}, retrieves its
     * Writer, writes each item in order, and returns the buffer.
     *
     * <p>No Reader is created here — callers decide when to create Readers
     * relative to writes (this matters for tests that exercise Reader
     * initialization semantics, e.g. Req 5).
     */
    @SafeVarargs
    static <T> RingBuffer<T> bufferWith(int n, T... items) {
        RingBuffer<T> buffer = new RingBuffer<>(n);
        RingBuffer<T>.Writer writer = buffer.getWriterInstance();
        for (T item : items) {
            writer.write(item);
        }
        return buffer;
    }

    /**
     * Reads from {@code r} repeatedly until {@code read()} returns {@code null}
     * and returns the collected list (in read order).
     *
     * <p>Note: {@code null} terminates the drain. The production contract
     * (Req 6.1) defines {@code null} as the "no new items" sentinel and the
     * Writer rejects {@code null} writes (Req 4.1), so a {@code null} return
     * unambiguously means "caught up".
     */
    static <T> List<T> drain(RingBuffer<T>.Reader r) {
        List<T> out = new ArrayList<>();
        T item;
        while ((item = r.read()) != null) {
            out.add(item);
        }
        return out;
    }
}
