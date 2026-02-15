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

        System.out.println("Reader 1: " + reader1.read());
        System.out.println("Reader 1: " + reader1.read());
        System.out.println("Reader 1: " + reader1.read());
        System.out.println("Reader 2: " + reader2.read());
        ringBuffer.printBuffer();

        writer.write(6);
        writer.write(7);
        System.out.println("Wrote 6, 7 to buffer");
        ringBuffer.printBuffer();

        System.out.println("Reader 2: " + reader2.read());
        System.out.println("Reader 2: " + reader2.read());
        ringBuffer.printBuffer();

        writer.write(8);
        writer.write(9);
        System.out.println("Wrote 8, 9 to buffer");
        ringBuffer.printBuffer();

        System.out.println("Reader 1: " + reader1.read());
        System.out.println("Reader 2: " + reader2.read());
        System.out.println("Reader 2: " + reader2.read());
        ringBuffer.printBuffer();

        writer.write(10);
        writer.write(11);
        writer.write(12);
        writer.write(13);
        System.out.println("Wrote 10-13 to buffer");
        ringBuffer.printBuffer();

        System.out.println("Reader 1: " + reader1.read());
        System.out.println("Reader 1: " + reader1.read());
        System.out.println("Reader 1: " + reader1.read());
        System.out.println("Reader 1: " + reader1.read());
        System.out.println("Reader 1: " + reader1.read());
        System.out.println("Reader 1: " + reader1.read());
        System.out.println("Reader 1: " + reader1.read());
        System.out.println("Reader 1: " + reader1.read());
        System.out.println("Reader 1: " + reader1.read());
        ringBuffer.printBuffer();
    }
}
