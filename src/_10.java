import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashSet;
import java.util.Set;

public class _10 {

    public static void main(String[] args){
        Thread t1 = new Thread(() -> {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            try (//with
                 Socket client = new Socket("DESKTOP-4NQL187", 4443);
                 PrintWriter out = new PrintWriter(client.getOutputStream(), true);
                 BufferedReader in = new BufferedReader(new InputStreamReader(client.getInputStream()));
                 BufferedReader stdIn = new BufferedReader(new InputStreamReader(System.in))
            ) {
                String line;
                while ((line = stdIn.readLine()) != null) {
                    out.println(line);
                    System.out.printf("from server : %s%n", in.readLine());
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        });

        Thread t2 = new Thread(()->{
            try {
                ServerSocket serverSocket = new ServerSocket(4443);
                while(true){
                    Socket client = serverSocket.accept();
                    Thread handlerThread =  new Thread(()->{
                        try {
                            PrintWriter out = new PrintWriter(client.getOutputStream(),true);
                            BufferedReader in = new BufferedReader(new InputStreamReader(client.getInputStream()));
                            String line;
                            while ((line=in.readLine())!=null){
                                out.println(Thread.currentThread().getName()+" "+Thread.currentThread().getId()+" : "+line);
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
