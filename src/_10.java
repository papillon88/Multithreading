import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;



//co-ordinator process

public class _10 {

    static int NUMBER_OF_PROCS ;
    static volatile int numberOfProcessRegistered = 0;
    static volatile int numberOfProcessReady = 0;
    static Object lock = new Object();
    static final String FILE_LOCATION = "C:\\Users\\papillon\\Desktop\\Multithreading\\src\\dsConfig";
    static Map<Integer,Set<Neighbour>> neighbours = new HashMap<>();
    static int ID;

    public static void main(String[] args){

        runConfiguration(FILE_LOCATION);

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
                                        numberOfProcessRegistered++;
                                        System.out.println("number of processes registered with coordinator so far : "+numberOfProcessRegistered);
                                    }
                                    while (true){
                                        if(numberOfProcessRegistered==NUMBER_OF_PROCS){
                                            break;
                                        }
                                    }
                                    Thread.sleep(200);
                                    //modify below code to apprise the clients about their host id and neighbours
                                    out.println("registered");
                                }
                                if(line.equalsIgnoreCase("ready")){
                                    synchronized (lock){
                                        numberOfProcessReady++;
                                        System.out.println("number of processes ready so far : "+numberOfProcessReady);
                                    }
                                    while (true){
                                        if(numberOfProcessReady==NUMBER_OF_PROCS){
                                            break;
                                        }
                                    }
                                    Thread.sleep(200);
                                    //modify below code to apprise the clients about their host id and neighbours
                                    System.out.println("sending compute to "+client.getInetAddress().getHostName());
                                    out.println("compute");
                                }
                            }
                            out.close();
                            in.close();
                            client.close();
                        } catch (IOException e) {
                            e.printStackTrace();
                        } catch (InterruptedException e) {
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

    private static void runConfiguration(String fileLocation) {
        try {
            BufferedReader reader = new BufferedReader(new FileReader(fileLocation));
            String line;
            while ((line=reader.readLine())!=null){
                String[] parsedLines = line.split(" ");
                if(parsedLines[0].equalsIgnoreCase("COORDINATOR"))
                    continue;
                if(parsedLines[0].equalsIgnoreCase("NUMBER")){
                    NUMBER_OF_PROCS=Integer.parseInt(parsedLines[3]);
                }
                if(parsedLines[0].equalsIgnoreCase("INTERVAL"))
                    continue;
                if(parsedLines[0].equalsIgnoreCase("TERMINATE"))
                    continue;
                if(parsedLines[0].equalsIgnoreCase("NEIGHBOR")){
                    continue;
                }
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}