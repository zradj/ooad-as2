import java.util.*;

public class RingBuffer<T> {

    public class Reader {

        private RingBuffer<T> buffer;

        private Reader() {}

        public T read() {
            return this.buffer.read(this);
        }
    }

    public class Writer {

        private RingBuffer<T> buffer;

        private Writer() {}

        public void write(T item) {
            this.buffer.write(item);
        }
    }

    private final int size;
    private final Object[] buffer;
    private final Map<Reader, Integer> readIndices;
    private Writer writer;
    private int writeIndex = 0;
    private int newestItemIndex = 0;

    public RingBuffer(int size) {
        this.size = size;
        this.buffer = new Object[size];
        this.readIndices = new HashMap<>();
    }

    private int nextIndex(int index) {
        return (index + 1) % this.size;
    }

    private void write(T item) {
        if (item == null) throw new NullPointerException("Null values are not supported");

        for (Map.Entry<Reader, Integer> entry : this.readIndices.entrySet()) {
            int readIndex = entry.getValue();
            if (readIndex == this.writeIndex && buffer[readIndex] != null)
                entry.setValue(nextIndex(readIndex));
        }

        this.buffer[this.writeIndex] = item;
        this.newestItemIndex = this.writeIndex;
        this.writeIndex = nextIndex(this.writeIndex);
    }

    private T read(Reader reader) {
        int readIndex = this.readIndices.get(reader);
        @SuppressWarnings("unchecked")
        T result = (T) this.buffer[readIndex];
        this.readIndices.put(reader, nextIndex(readIndex));
        return result;
    }

    public Reader createReader() {
        Reader reader = new Reader();
        this.readIndices.put(reader, this.newestItemIndex);
        return reader;
    }

    public Writer getWriterInstance() {
        if (this.writer == null) {
            this.writer = new Writer();
        }
        return this.writer;
    }

    public void printBuffer() {
        System.out.println(Arrays.toString(this.buffer));
    }
}
