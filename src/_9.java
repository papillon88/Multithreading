import java.security.SecureRandom;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class _9 {

    public static void main(String[] args) {
        Runner2 runner2 = new Runner2();
        Thread t1 = new Thread(()-> runner2.firstThread());
        Thread t2 = new Thread(()-> runner2.secondThread());
        t1.start();
        t2.start();

        try {
            t1.join();
            t2.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        runner2.finished();
    }
}

class Runner2 {

    private Account acc1 = new Account();
    private Account acc2 = new Account();
    SecureRandom secureRandom = new SecureRandom();
    private Lock lock1 = new ReentrantLock();
    private Lock lock2 = new ReentrantLock();

    private void accquireLocks(Lock l1, Lock l2){
        boolean gotL1 = false;
        boolean gotL2 = false;
        while (true){
            try {
                gotL1 = l1.tryLock();
                gotL2 = l2.tryLock();
            } finally {
               if(gotL1 && gotL2)
                   return;
               if(gotL1){
                   l1.unlock();
                   //return;
               }
               if(gotL2) {
                   l2.unlock();
                   //return;
               }
            }
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    public void firstThread() {
        for(int i=0;i<10;i++){
            accquireLocks(lock1,lock2);
            try {
                Account.transfer(acc1,acc2,secureRandom.nextInt(20));
            } finally {
                lock2.unlock();
                lock1.unlock();
            }
        }
    }


    public void secondThread() {
        for(int i=0;i<10;i++){
            accquireLocks(lock2,lock1);
            try {
                Account.transfer(acc2,acc1,secureRandom.nextInt(20));
            } finally {
                lock1.unlock();
                lock2.unlock();
            }
        }
    }

    public void finished() {
        System.out.printf("Account 1 balance : %d%n",acc1.getBalance());
        System.out.printf("Account 2 balance : %d%n",acc2.getBalance());
        System.out.printf("Total balance : %d",acc1.getBalance() + acc2.getBalance());
    }
}

class Account {

    private int balance = 1000;

    public void deposit(int x){
        this.balance += x;
    }

    public void withdraw(int x){
        this.balance -= x;
    }

    public int getBalance(){
        return this.balance;
    }

    public static void transfer(Account account1 , Account account2, int amount) {
        account1.withdraw(amount);
        account2.deposit(amount);
    }
}
