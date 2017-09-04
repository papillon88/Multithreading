import java.util.LinkedList;
import java.util.List;
import java.util.Scanner;

public class _6 {

    private void produce(){
        synchronized (this){
            System.out.println("Produce start");
            try {
                System.out.println("Produce wait : Hit Enter");
                Scanner scanner = new Scanner(System.in);
                scanner.nextLine();
                this.wait();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            System.out.println("Produce resume");
        }
    }

    private void consume(){
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        synchronized (this){
            System.out.println("Consume start");
            Scanner scanner = new Scanner(System.in);
            scanner.nextLine();
            this.notify();
            try {
                Thread.sleep(3000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            System.out.println("Consume end");
        }
    }

    public static void main(String[] args) {

        _6 cl = new _6();
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

}
