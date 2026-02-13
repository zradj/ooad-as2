public class Main {

    public static void main(String[] args) {
        RingBuffer<Integer> ringBuffer = new RingBuffer<>(5);

        ringBuffer.write(1);
        ringBuffer.write(2);
        ringBuffer.write(3);
        ringBuffer.write(4);
        ringBuffer.write(5);
        System.out.println("Wrote 1-5 to buffer");

        ringBuffer.printBuffer();

        ringBuffer.write(6);
        System.out.println("Wrote 6 to buffer");
        ringBuffer.printBuffer();

        System.out.println("Read: " + ringBuffer.read());
        System.out.println("Read: " + ringBuffer.read());

        ringBuffer.printBuffer();

        ringBuffer.write(7);
        System.out.println("Wrote 7 to buffer");
        ringBuffer.printBuffer();

        System.out.println("Read: " + ringBuffer.read());
        ringBuffer.printBuffer();
    }
}
