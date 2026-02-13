public class Main {

    public static void main(String[] args) {
        RingBuffer<Integer> ringBuffer = new RingBuffer<>(5);

        RingBuffer<Integer>.Reader reader1 = ringBuffer.createReader();
        RingBuffer<Integer>.Reader reader2 = ringBuffer.createReader();

        RingBuffer<Integer>.Writer writer = ringBuffer.getWriterInstance();

        writer.write(1);
        writer.write(2);
        writer.write(3);
        writer.write(4);
        writer.write(5);
        System.out.println("Wrote 1-5 to buffer");
        ringBuffer.printBuffer();
    }
}
