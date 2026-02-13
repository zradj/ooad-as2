import java.util.Arrays;

public class RingBuffer<T> {

    private final int size;
    private final Object[] buffer;
    private int writeIndex = 0;
    private int readIndex = 0;

    public RingBuffer(int size) {
        this.size = size;
        this.buffer = new Object[size];
    }

    private int nextIndex(int index) {
        return (index + 1) % this.size;
    }

    public void add(T item) {
        if (item == null) throw new NullPointerException("Null values are not supported");

        if (this.writeIndex == this.readIndex && this.buffer[this.readIndex] != null)
            this.readIndex = nextIndex(this.readIndex);
        this.buffer[this.writeIndex] = item;

        this.writeIndex = nextIndex(this.writeIndex);
    }

    public T read() {
        @SuppressWarnings("unchecked")
        T result = (T) this.buffer[this.readIndex];
        this.buffer[this.readIndex] = null;
        this.readIndex = nextIndex(this.readIndex);
        return result;
    }

    public void printBuffer() {
        System.out.println(Arrays.toString(this.buffer));
    }
}
