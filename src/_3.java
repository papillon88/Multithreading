import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;

class Worker {

    private SecureRandom secureRandom = new SecureRandom();
    private List<Integer> list1 = new ArrayList<>();
    private List<Integer> list2 = new ArrayList<>();

    private Object lock1 = new Object();
    private Object lock2 = new Object();

    public void stageOne(){
        synchronized (lock1){
            try {
                Thread.sleep(1);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            list1.add(secureRandom.nextInt(100));
        }
    }

    public void stageTwo(){
        synchronized (lock2){
            try {
                Thread.sleep(1);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            list2.add(secureRandom.nextInt(100));
        }
    }

    public void process(){
        for(int i=0;i<1000;i++){
            stageOne();
            stageTwo();
        }
    }

    public void main() {
        long start = System.currentTimeMillis();
        Thread t1 = new Thread(()-> process());
        Thread t2 = new Thread(()-> process());
        t1.start();
        t2.start();
        try {
            t1.join();
            t2.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        long end = System.currentTimeMillis();
        System.out.printf("Time taken %d, List1 size %d, List2 size %d%n",(end-start),list1.size(),list2.size());
    }
}


public class _3 {

    public static void main(String[] args) {
        int count = 200;
        while(count>0){
            new Worker().main();
            count--;
        }
    }
}
