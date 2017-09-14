import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashSet;
import java.util.Set;



//co-ordinator process

public class _10 {

    static final int NUMBER_OF_PROCS = 3;
    static volatile int numberOfProcesses = 0;
    static Object lock = new Object();

    public static void main(String[] args){

        Thread t = new Thread(()->{
            System.out.println("Coordinator process initiated");
            System.out.println("Waiting for processes to register...");
            try {
                ServerSocket serverSocket = new ServerSocket(5000);
                while(true){
                    Socket client = serverSocket.accept();
                    System.out.println("A register request received");
                    Thread handlerThread =  new Thread(()->{
                        try {
                            PrintWriter out = new PrintWriter(client.getOutputStream(),true);
                            BufferedReader in = new BufferedReader(new InputStreamReader(client.getInputStream()));
                            String line;
                            while ((line=in.readLine())!=null){
                                if(line.equalsIgnoreCase("register")){
                                    synchronized (lock){
                                        numberOfProcesses++;
                                        System.out.println("number of processes registered with coordinator so far : "+numberOfProcesses);
                                    }
                                    while (true){
                                        if(numberOfProcesses==NUMBER_OF_PROCS){
                                            break;
                                        }
                                    }
                                    out.println("registered");
                                }
                            }
                            out.close();
                            in.close();
                            client.close();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    });
                    handlerThread.start();
                    //handlerThread.join();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        });

        t.start();
        try {
            t.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}