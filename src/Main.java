public class Main {

    public static void main(String[] args) {
        RingBuffer<Integer> ringBuffer = new RingBuffer<>(5);

        ringBuffer.add(1);
        ringBuffer.add(2);
        ringBuffer.add(3);
        ringBuffer.add(4);
        ringBuffer.add(5);
        System.out.println("Wrote 1-5 to buffer");

        ringBuffer.printBuffer();

        ringBuffer.add(6);
        System.out.println("Wrote 6 to buffer");
        ringBuffer.printBuffer();

        System.out.println("Read: " + ringBuffer.read());
        System.out.println("Read: " + ringBuffer.read());

        ringBuffer.printBuffer();

        ringBuffer.add(7);
        System.out.println("Wrote 7 to buffer");
        ringBuffer.printBuffer();

        System.out.println("Read: " + ringBuffer.read());
        ringBuffer.printBuffer();
    }
}
