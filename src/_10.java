import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;

public class _10 {

    public static void main(String[] args){
        Thread t1 = new Thread(()->{
            try (//with
                    Socket client = new Socket("DESKTOP-4NQL187",4443);
                    PrintWriter out = new PrintWriter(client.getOutputStream(),true);
                    BufferedReader in = new BufferedReader(new InputStreamReader(client.getInputStream()));
                    BufferedReader stdIn = new BufferedReader(new InputStreamReader(System.in))
            ) {
                Thread.sleep(3000);
                String line;
                while((line=stdIn.readLine())!=null){
                    out.println(line);
                    System.out.printf("from server : %s%n",in.readLine());
                }
            } catch (IOException e) {
                e.printStackTrace();
            } catch (InterruptedException e){
                        e.printStackTrace();
            }
        });
        Thread t2 = new Thread(()->{
            try(//with
                    ServerSocket serverSocket = new ServerSocket(4443);
                    Socket client = serverSocket.accept();
                    PrintWriter out = new PrintWriter(client.getOutputStream(),true);
                    BufferedReader in = new BufferedReader(new InputStreamReader(client.getInputStream()))
            ) {
                String line;
                while ((line=in.readLine())!=null){
                    out.println(line);
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
}
