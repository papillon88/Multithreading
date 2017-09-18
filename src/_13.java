import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

public class _13 {

    static volatile boolean canSendHello = false;
    static volatile boolean canSendReady = true;
    static volatile boolean canSendCompute = false;
    static BlockingQueue<Integer> blockingQueue = new ArrayBlockingQueue<>(1);
    static volatile int numberOfNeighbours = 0;
    static volatile int neighbourCounter = 0;
    static Object lock = new Object();
    static final String FILE_LOCATION = "C:\\Users\\papillon\\Desktop\\Multithreading\\src\\3";
    static Set<Neighbour> neighbours = new HashSet<>();
    static int PROCESSID;

    public static void main(String[] args){

        Thread t0 = new Thread(()->{
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            //register with coordinator
            try {
                BufferedReader reader = new BufferedReader(new FileReader(FILE_LOCATION));
                String[] params = reader.readLine().split(" ");
                Socket client = new Socket(params[1], Integer.parseInt(params[2]));
                PrintWriter out = new PrintWriter(client.getOutputStream(), true);
                BufferedReader in = new BufferedReader(new InputStreamReader(client.getInputStream()));
                System.out.println("registration initiated");
                out.println("register");
                String line;
                while ((line=in.readLine())!=null){
                    String[] parsedReceivedLine = line.split(",");
                    if(parsedReceivedLine[0].equals("registered")) {
                        System.out.println("registration success");
                        PROCESSID=Integer.parseInt(parsedReceivedLine[1]);
                        for(int i=2;i<parsedReceivedLine.length;i++){
                            String[] neighboursToBeConfigured = parsedReceivedLine[i].split(":");
                            //neighbours.add(new Neighbour(neighboursToBeConfigured[0],neighboursToBeConfigured[1]));
                            System.out.println(parsedReceivedLine[i]);
                        }
                        synchronized (lock){
                            canSendHello=true;
                        }
                    }
                    while(true){
                        if(!blockingQueue.isEmpty())
                            break;
                    }
                    if(canSendReady){
                        System.out.println("sending ready to coord");
                        out.println("ready");
                        canSendReady=false;
                    }
                    if(line.equals("compute")) {
                        System.out.println("computation starting...");
                        synchronized (lock){
                            canSendCompute=true;
                        }
                    }
                }
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
        });

        Thread t1 = new Thread(() -> {
            while(true){
                if(canSendHello)
                    break;
                try {
                    Thread.sleep(900);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }

            try {
                Thread.sleep(900);
                BufferedReader fileReader = new BufferedReader(new FileReader(FILE_LOCATION));
                String lineRead;
                fileReader.readLine();
                while((lineRead=fileReader.readLine())!=null){
                    String[] params = lineRead.split(" ");
                    Neighbour neighbour = new Neighbour(params[0],params[1]);
                    neighbours.add(neighbour);
                    numberOfNeighbours++;
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
                        out.println("hello");
                        while(true) {
                            if(canSendCompute){
                                out.println("from 13");
                                Thread.sleep(10000);
                            }
                            Thread.sleep(900);
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
                ServerSocket serverSocket = new ServerSocket(5557);
                while(true){
                    Socket client = serverSocket.accept();
                    Thread handlerThread =  new Thread(()->{
                        try {
                            PrintWriter out = new PrintWriter(client.getOutputStream(),true);
                            BufferedReader in = new BufferedReader(new InputStreamReader(client.getInputStream()));
                            String line;
                            while ((line=in.readLine())!=null){
                                if(line.equalsIgnoreCase("hello")){
                                    System.out.println(line);
                                    synchronized (lock){
                                        neighbourCounter++;
                                    }
                                    if(neighbourCounter==numberOfNeighbours)
                                        blockingQueue.add(1);
                                } else {
                                    System.out.println(line);
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
}