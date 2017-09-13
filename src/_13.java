import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashSet;
import java.util.Set;

public class _13 {

    static volatile boolean canTalkWithEachOther = false;
    static Object lock = new Object();

    public static void main(String[] args){

        Thread t0 = new Thread(()->{
            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            //register with coordinator
            try {
                BufferedReader reader = new BufferedReader(new FileReader("C:\\Users\\papillon\\Desktop\\Multithreading\\src\\3"));
                String[] params = reader.readLine().split(" ");
                Socket client = new Socket(params[1], Integer.parseInt(params[2]));
                PrintWriter out = new PrintWriter(client.getOutputStream(), true);
                out.println("register");
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
        });

        Thread t1 = new Thread(() -> {
            while (true){
                if(canTalkWithEachOther)
                    break;
                try {
                    Thread.sleep(400);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            Set<Neighbour> neighbours = new HashSet<>();
            try {
                Thread.sleep(9000);
                BufferedReader fileReader = new BufferedReader(new FileReader("C:\\Users\\papillon\\Desktop\\Multithreading\\src\\1"));
                String lineRead;
                while((lineRead=fileReader.readLine())!=null){
                    String[] params = lineRead.split(" ");
                    Neighbour neighbour = new Neighbour(params[0],params[1]);
                    neighbours.add(neighbour);
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
            //for each neighbour, create new thread and establish new socket
            for(Neighbour n : neighbours){
                Thread tx = new Thread(()->{
                    String line;
                    try {
                        Socket client = new Socket(n.getHostname(), Integer.parseInt(n.getPortnum()));
                        PrintWriter out = new PrintWriter(client.getOutputStream(), true);
                        BufferedReader in = new BufferedReader(new InputStreamReader(client.getInputStream()));
                        BufferedReader stdIn = new BufferedReader(new InputStreamReader(System.in));
                        while (true) {
                            out.println("from 10");
                            Thread.sleep(10000);
                            //System.out.printf("from server : %s%n", in.readLine());
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                });
                tx.start();
            }
        });


        Thread t2 = new Thread(()->{
            try {
                ServerSocket serverSocket = new ServerSocket(5556);
                while(true){
                    Socket client = serverSocket.accept();
                    Thread handlerThread =  new Thread(()->{
                        try {
                            PrintWriter out = new PrintWriter(client.getOutputStream(),true);
                            BufferedReader in = new BufferedReader(new InputStreamReader(client.getInputStream()));
                            String line;
                            while ((line=in.readLine())!=null){
                                //out.println(Thread.currentThread().getName()+" "+Thread.currentThread().getId()+" : "+line);
                                if(line.equalsIgnoreCase("registered")){
                                    synchronized (lock){
                                        canTalkWithEachOther=true;
                                    }
                                }
                                //System.out.println(line);
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

        t0.start();
        t1.start();
        t2.start();

        try {
            t0.join();
            t1.join();
            t2.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private static void startAsNonCoordinator() {
        startServer();
        startClient();
    }

    private static void startAsCoordinator() {

    }

    private static void startClient() {

    }

    private static void startServer() {

    }
}