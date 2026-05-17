import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Reader sequence-number initialization tests for {@link RingBuffer} (Requirement 5).
 *
 * <p>Lives in the default (unnamed) Java package because {@code RingBuffer}
 * is in the default package and named-package callers cannot import
 * default-package types.
 *
 * <p>Production semantics under test: {@code createReader()} snapshots the
 * current {@code globalSeq} as the Reader's starting {@code sequenceNum}.
 * A Reader created after {@code M} writes therefore observes
 * {@code globalSeq <= sequenceNum} until a fresh write advances
 * {@code globalSeq}. The Reader's first non-null read returns the item
 * written at {@code sequenceNum + 1} — i.e., the first item written
 * <em>after</em> the Reader was created, never an item written before it.
 *
 * <p>Validates Property 2 (Reader Independence / Non-destructive Read):
 * each Reader's first non-null read is determined solely by its own
 * starting {@code sequenceNum}, independently of any other Reader's
 * starting position or read activity.
 *
 * <p><b>Validates: Requirements 5.1, 5.2, 5.3</b>
 */
class ReaderInitializationTest {

    /** Fixed capacity used by the parameterized first-test method. */
    private static final int N = 5;

    // ------------------------------------------------------------------
    // Requirement 5.1 — Reader created after M writes (M < N) returns null
    // on its first read() because no writes have advanced globalSeq past
    // the Reader's snapshot of it.
    // ------------------------------------------------------------------

    @ParameterizedTest(name = "M={0} writes before Reader creation (N=" + N + ")")
    @ValueSource(ints = {0, 1, N - 1})
    @DisplayName("Reader created after M writes (M < N) returns null on its first read()")
    void readerCreatedAfterMWritesUnderCapacity_returnsNullOnFirstRead(int m) {
        RingBuffer<String> buffer = BufferFixtures.emptyBuffer(N);
        RingBuffer<String>.Writer writer = buffer.getWriterInstance();

        for (int i = 0; i < m; i++) {
            writer.write("pre-" + i);
        }

        // Reader snapshots globalSeq == m - 1 (or -1 when m == 0).
        // Either way, no writes after creation -> globalSeq <= sequenceNum -> null.
        RingBuffer<String>.Reader reader = buffer.createReader();

        assertNull(reader.read(),
                "Reader created after " + m + " writes must return null on its first read(): "
                        + "no writes have occurred since createReader() snapshotted globalSeq");
    }

    // ------------------------------------------------------------------
    // Requirement 5.2 — Reader created after M writes, then ONE more write,
    // returns only the newly written item — never any item written before
    // its creation. A subsequent read() returns null (only one new item exists).
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Reader created after M writes, then one more write: read() returns only the new item")
    void readerCreatedAfterMWrites_thenOneMoreWrite_returnsOnlyTheNewItem() {
        final int m = 3; // M < N; chose 3 to ensure several "before" items exist.
        RingBuffer<String> buffer = BufferFixtures.emptyBuffer(N);
        RingBuffer<String>.Writer writer = buffer.getWriterInstance();

        // Items written BEFORE the Reader is created — these must never be observed.
        writer.write("before-0");
        writer.write("before-1");
        writer.write("before-2");

        RingBuffer<String>.Reader reader = buffer.createReader();

        // One write AFTER Reader creation. This is the only item the Reader should ever see.
        writer.write("after");

        String first = reader.read();
        assertSame("after", first,
                "Reader's first non-null read() must return the item written immediately after "
                        + "createReader(), not any of the M items written before it");

        String second = reader.read();
        assertNull(second,
                "After consuming the single post-creation item, the Reader must return null "
                        + "until further writes occur (no items written before creation are returned)");
    }

    // ------------------------------------------------------------------
    // Requirement 5.3 — Multiple Readers created at different points each
    // start at their own first new item, demonstrating per-Reader
    // sequenceNum independence.
    //
    // Timeline (N = 10, well above capacity needs):
    //   t0: createReader() -> Reader A (sequenceNum = -1, globalSeq = -1)
    //   t1: write "A1"     (globalSeq = 0)
    //   t2: write "A2"     (globalSeq = 1)
    //   t3: createReader() -> Reader B (sequenceNum = 1)
    //   t4: write "B1"     (globalSeq = 2)
    //   t5: write "B2"     (globalSeq = 3)
    //
    // Reader A's first read() advances sequenceNum from -1 to 0 -> "A1".
    // Reader B's first read() advances sequenceNum from  1 to 2 -> "B1".
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Multiple Readers created at different points: each starts at its own first new item")
    void multipleReadersCreatedAtDifferentPoints_eachStartsAtItsOwnFirstNewItem() {
        RingBuffer<String> buffer = BufferFixtures.emptyBuffer(10);
        RingBuffer<String>.Writer writer = buffer.getWriterInstance();

        // Reader A is created BEFORE any writes, so it snapshots globalSeq == -1.
        RingBuffer<String>.Reader readerA = buffer.createReader();

        // Two writes occur after Reader A's creation but before Reader B's.
        writer.write("A1");
        writer.write("A2");

        // Reader B snapshots globalSeq == 1 (two writes have advanced it from -1 to 1).
        RingBuffer<String>.Reader readerB = buffer.createReader();

        // Two more writes occur after Reader B's creation.
        writer.write("B1");
        writer.write("B2");

        // Reader A's first non-null read is the first item written after its creation: "A1".
        assertSame("A1", readerA.read(),
                "Reader A (created before any writes) must return \"A1\" — the first item "
                        + "written after its createReader() snapshot");

        // Reader B's first non-null read is the first item written after ITS creation: "B1".
        // It must skip "A1" and "A2", which were written before B was created.
        assertSame("B1", readerB.read(),
                "Reader B (created after \"A1\" and \"A2\") must return \"B1\" — the first item "
                        + "written after its own createReader() snapshot, not any earlier item");
    }
}
