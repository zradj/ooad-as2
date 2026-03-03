import java.util.*;

public class RingBuffer<T> {

    public class Reader {

        private int sequenceNum;

        private Reader() {
            this.sequenceNum = RingBuffer.this.getSequenceNum();
        }

        public T read() {
            if (RingBuffer.this.getSequenceNum() <= this.sequenceNum) {
                return null;
            }

            if (RingBuffer.this.getSequenceNum() - this.sequenceNum > RingBuffer.this.getSize()) {
                this.sequenceNum = RingBuffer.this.getSequenceNum() - RingBuffer.this.getSize() + 1;
            } else {
                this.sequenceNum++;
            }

            int index = computeIndex(this.sequenceNum);
            return RingBuffer.this.read(index);
        }
    }

    public class Writer {

        private final RingBuffer<T> buffer;

        private Writer(RingBuffer<T> buffer) {
            this.buffer = buffer;
        }

        public void write(T item) {
            this.buffer.write(item);
        }
    }

    private final int size;
    private final Object[] buffer;
    private Writer writer;
    private int sequenceNum = -1;

    public RingBuffer(int size) {
        this.size = size;
        this.buffer = new Object[size];
    }

    private int computeIndex(int sequenceNum) {
        return sequenceNum % this.size;
    }

    private void write(T item) {
        if (item == null) throw new NullPointerException("Null values are not supported");

        this.sequenceNum++;
        int index = computeIndex(this.sequenceNum);
        this.buffer[index] = item;
    }

    private T read(int index) {
        //noinspection unchecked
        return (T) this.buffer[index];
    }

    private int getSequenceNum() {
        return this.sequenceNum;
    }

    public int getSize() {
        return this.size;
    }

    public Reader createReader() {
        return new Reader();
    }

    public Writer getWriterInstance() {
        if (this.writer == null) {
            this.writer = new Writer(this);
        }
        return this.writer;
    }

    public void printBuffer() {
        System.out.println(Arrays.toString(this.buffer));
    }
}
