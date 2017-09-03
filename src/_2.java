import org.omg.PortableServer.THREAD_POLICY_ID;

public class _2 {

    private int count = 0;

    private synchronized void increment(){
        count++;
    }

    public static void main(String[] args) {
        int i = 50;
        _2 runner = new _2();
        do {
            runner.work();
            System.out.println(runner.count);
            i--;
            runner.count = 0;
        } while (i>0);

    }

    private void work() {
        Thread t1 = new Thread(()->{
           for(int i=0;i<100;i++)
               increment();
        });
        Thread t2 = new Thread(()->{
            for(int i=0;i<100;i++)
                increment();
        });

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
