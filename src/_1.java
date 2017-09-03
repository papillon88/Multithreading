import java.util.Scanner;

public class _1 {

    private static volatile boolean running = true;

    public static void main(String[] args) {
        Thread t = new Thread(()->{
            while (running) {
                System.out.println("hello");
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        });

        t.start();
        Scanner scanner = new Scanner(System.in);
        scanner.nextLine();

        toggleRunning();
    }

    private static void toggleRunning(){
        running = !running;
    }
}
