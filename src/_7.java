import java.security.SecureRandom;
import java.util.LinkedList;
public class _7 {

    public static void main(String[] args) {


        _7 cl = new _7();
        Thread t1 = new Thread(cl::produce);
        Thread t2 = new Thread(cl::consume);

        t1.start();
        t2.start();

        try {
            t1.join();
            t2.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private final int LIMIT = 2;
    private LinkedList<Integer> list = new LinkedList<>();
    private SecureRandom random = new SecureRandom();
    private Object lock = new Object();

    private void consume() {
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        while (true){
                synchronized (lock){
                    int elem = list.removeFirst();
                    System.out.printf("Consume %d%n",elem);
                    while(list.size() == 0){
                        try {
                            lock.wait();
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                    lock.notify();
                }
            }
    }

    private void produce() {

            while (true){
                synchronized (lock){
                int insert = random.nextInt(100);
                list.add(insert);
                System.out.printf("Produce %d%n", insert);
                while(list.size() == LIMIT) {
                    try {
                        lock.wait();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
                lock.notify();
            }
        }
    }
}
