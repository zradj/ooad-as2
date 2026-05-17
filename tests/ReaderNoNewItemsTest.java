import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Tests for Requirement 6 — Reader Read - No New Items.
 *
 * <p>Validates Property 8 (Empty / Caught-Up Read Returns Null): for any Reader
 * that has consumed every item up to {@code globalSeq}, every subsequent
 * {@code read()} returns {@code null} until the next {@code Writer.write(...)}
 * call, after which exactly one {@code read()} returns the new item.
 *
 * <p>Strategy:
 *
 * <ul>
 *   <li>Construct a buffer with capacity strictly larger than the number of
 *       writes so no Capacity_Wrap or Catch_Up_Jump is triggered. Reader is
 *       created <em>before</em> any writes (via {@code bufferWith} followed by
 *       {@code createReader}? — note: {@code bufferWith} creates the buffer and
 *       writes <em>before</em> any Reader is created, so the Reader created
 *       here starts caught up to {@code globalSeq}, which is what Req 6.1
 *       describes for the "no unread items" condition. To exercise the
 *       "Reader has read all items written so far" case we must write
 *       <em>before</em> the Reader is created so the Reader's initial
 *       {@code sequenceNum} already equals {@code globalSeq}, OR write
 *       <em>after</em> creating the Reader and drain it. We use the latter
 *       wherever we need to confirm items can be observed first.)
 * </ul>
 *
 * <p>Validates: Requirements 6.1, 6.2, 6.3
 */
class ReaderNoNewItemsTest {

    @Test
    @DisplayName("6.1: Reader that has read every available item returns null on next read")
    void readerWithNoUnreadItems_returnsNullOnRead() {
        // Given a buffer of capacity 5 with a Reader created BEFORE any writes,
        // then 3 writes occur (well under capacity, no overrun), and the Reader
        // has consumed all 3.
        RingBuffer<String> buffer = BufferFixtures.emptyBuffer(5);
        RingBuffer<String>.Reader reader = buffer.createReader();
        RingBuffer<String>.Writer writer = buffer.getWriterInstance();

        writer.write("a");
        writer.write("b");
        writer.write("c");

        assertEquals("a", reader.read(), "first item must be readable");
        assertEquals("b", reader.read(), "second item must be readable");
        assertEquals("c", reader.read(), "third item must be readable");

        // When the Reader has caught up (sequenceNum == globalSeq),
        // Then the next read returns null (Req 6.1).
        assertNull(reader.read(), "caught-up Reader must return null on next read");
    }

    @Test
    @DisplayName("6.2: Consecutive reads with no intervening writes keep returning null")
    void consecutiveReadsWithNoNewWrites_continueReturningNull() {
        // Given a fully drained Reader (caught up to globalSeq).
        RingBuffer<Integer> buffer = BufferFixtures.emptyBuffer(4);
        RingBuffer<Integer>.Reader reader = buffer.createReader();
        RingBuffer<Integer>.Writer writer = buffer.getWriterInstance();

        writer.write(1);
        writer.write(2);
        assertEquals(1, reader.read());
        assertEquals(2, reader.read());

        // When no writes occur and the Reader is polled repeatedly,
        // Then every read continues to return null (Req 6.2). The Reader's
        // internal state must not advance past globalSeq on a null read.
        assertNull(reader.read(), "1st post-drain read must be null");
        assertNull(reader.read(), "2nd post-drain read must be null");
        assertNull(reader.read(), "3rd post-drain read must be null");
        assertNull(reader.read(), "4th post-drain read must be null");
        assertNull(reader.read(), "5th post-drain read must be null");
    }

    @Test
    @DisplayName("6.3: After null, the next write becomes the next read's value")
    void nullThenWrite_nextReadReturnsNewItem() {
        // Given a Reader that has just observed null (caught up).
        RingBuffer<String> buffer = BufferFixtures.emptyBuffer(3);
        RingBuffer<String>.Reader reader = buffer.createReader();
        RingBuffer<String>.Writer writer = buffer.getWriterInstance();

        writer.write("first");
        assertEquals("first", reader.read());
        assertNull(reader.read(), "Reader must be caught up before the next write");

        // When a new write occurs after the null observation,
        // Then the Reader's next read returns exactly that new item — not the
        // previously observed item, not null, and not anything from a
        // catch-up jump (Req 6.3). Because we are still well under capacity,
        // the Reader's sequenceNum advances by exactly 1.
        writer.write("second");
        assertEquals("second", reader.read(),
                "next read after null+write must return the newly written item");

        // And the Reader is once again caught up — a follow-up read returns null.
        assertNull(reader.read(),
                "after consuming the new item the Reader is caught up again");
    }
}
