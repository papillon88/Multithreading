import java.util.Scanner;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

class Runner1 {

    private int count = 0;
    private void increment(){
        for(int i=0;i<100;i++){
            count++;
        }
    }

    private Lock lock = new ReentrantLock();
    private Condition condition = lock.newCondition();


    public void firstThread() {
        lock.lock();
        try {
            System.out.println("First Thread : wait");
            condition.await();
            System.out.println("First Thread : increment");
            increment();
        } catch (Exception e) {

        } finally {
            lock.unlock();
        }
    }


    public void secondThread() {
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        lock.lock();
        System.out.println("press return");
        new Scanner(System.in).nextLine();
        condition.signal();
        try {
            System.out.println("Second Thread : increment");
            increment();
        } finally {
            lock.unlock();
        }
    }

    public int finished() {
        return count;
    }
}



public class _8 {

    public static void main(String[] args) {
        Runner1 runner = new Runner1();
        Thread t1 = new Thread(()-> runner.firstThread());
        Thread t2 = new Thread(()-> runner.secondThread());
        t1.start();
        t2.start();

        try {
            t1.join();
            t2.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        System.out.println(runner.finished());
    }
}
