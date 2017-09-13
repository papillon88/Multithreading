import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashSet;
import java.util.Set;

public class _11 {

    public static void main(String[] args){
        Thread t1 = new Thread(() -> {
            Set<Neighbour> neighbours = new HashSet<>();
            try {
                Thread.sleep(9000);
                BufferedReader fileReader = new BufferedReader(new FileReader("C:\\Users\\papillon\\Desktop\\Multithreading\\src\\2"));
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
                            out.println("from 11");
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
                ServerSocket serverSocket = new ServerSocket(5555);
                while(true){
                    Socket client = serverSocket.accept();
                    System.out.println(client.getPort());
                    Thread handlerThread =  new Thread(()->{
                        try {
                            PrintWriter out = new PrintWriter(client.getOutputStream(),true);
                            BufferedReader in = new BufferedReader(new InputStreamReader(client.getInputStream()));
                            String line;
                            while ((line=in.readLine())!=null){
                                //out.println(Thread.currentThread().getName()+" "+Thread.currentThread().getId()+" : "+line);
                                System.out.println(line);
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

        t1.start();
        t2.start();

        try {
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