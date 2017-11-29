import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

public class deploy {

    //CLOCK
    static volatile int llc_value= 0;
    static Object llc_lock = new Object();

    //COORDINATOR PROCESS PARAMS
    static int NUMBER_OF_PROCS;
    static volatile int numberOfProcessRegistered = 0;
    static volatile int numberOfProcessReady = 0;
    static Object coordinatorLock = new Object();
    static final String CONFIG = "/home/013/d/dx/dxc141530/dsConfig";
    static Map<Integer, Set<Integer>> neighbours = new HashMap<>();
    static Map<Integer, Neighbour> pidToHostnameMap = new HashMap<>();
    static int PROCESSID;

    //NON COORDINATOR PROCESS PARAMS
    static volatile boolean canSendHello = false;
    static volatile boolean canSendReady = true;
    static volatile boolean canSendCompute = false;
    static BlockingQueue<Integer> blockingQueue = new ArrayBlockingQueue<>(1);
    static volatile int numberOfNeighbours = 0;
    static volatile int neighbourCounter = 0;
    static Object lock = new Object();
    static Set<Neighbour> localNeighbourSet = new HashSet<>();
    static volatile int[] sendArray;
    static volatile int[] recvArray;

    public static void main(String[] args) {

        if (args.length != 0) {
            if (args[0].equalsIgnoreCase("-c")) {
                //Running the coordinator part of the process
                runConfiguration(CONFIG);

                Thread coordinatorThread = new Thread(() -> {
                    System.out.println();
                    System.out.println("<i> ********Coordinator process initiated");
                    System.out.println("<i> ********Waiting for processes to register...");
                    System.out.println();
                    try {
                        ServerSocket serverSocket = new ServerSocket(5001);
                        while (true) {
                            Socket client = serverSocket.accept();
                            Thread handlerThread = new Thread(() -> {
                                Thread clThread = new Thread(()->{
                                    //keep a track of llc_value. initiate CLA every 100 llc_value
                                });
                                clThread.start();

                                try {
                                    PrintWriter out = new PrintWriter(client.getOutputStream(), true);
                                    BufferedReader in = new BufferedReader(new InputStreamReader(client.getInputStream()));
                                    String line;
                                    int pid;
                                    while ((line = in.readLine()) != null) {
                                        String[] lineRecvdByCoord = line.split(",");
                                        if (lineRecvdByCoord[1].equalsIgnoreCase("register")) {
                                            synchronized (coordinatorLock) {
                                                numberOfProcessRegistered++;
                                                pid = numberOfProcessRegistered;
                                                pidToHostnameMap.put(pid,
                                                        new Neighbour(String.valueOf(client.getInetAddress().getHostName()),pid));
                                                synchronized (llc_lock){
                                                    int senderProcTS = Integer.parseInt(lineRecvdByCoord[0]);
                                                    int maxOfTS = Math.max(senderProcTS,llc_value);
                                                    llc_value=maxOfTS+1;
                                                    System.out.printf("<%d> ********register frm : %s , # of procs registered : %d%n",llc_value,client.getInetAddress().getHostName(),numberOfProcessRegistered);
                                                }
                                            }
                                            while (true) {
                                                if (numberOfProcessRegistered == NUMBER_OF_PROCS) {
                                                    break;
                                                }
                                            }
                                            System.out.println();
                                            Thread.sleep(200);
                                            //modify below code to apprise the clients about their host id and neighbours
                                            StringBuilder stringBuilder = new StringBuilder();
                                            stringBuilder.append(pid);
                                            Set<Integer> tempSet = neighbours.get(pid);
                                            for (Integer i : tempSet) {
                                                stringBuilder.append(",");
                                                Neighbour neighbour = pidToHostnameMap.get(i);
                                                stringBuilder.append(neighbour.getHostname()+" "+neighbour.getId());
                                            }
                                            synchronized (llc_lock){
                                                llc_value++;
                                                out.println(llc_value+",registered," + stringBuilder.toString());
                                                System.out.printf("<%d> ********sending registered to %s%n",llc_value,client.getInetAddress().getHostName());
                                            }
                                        }
                                        if (lineRecvdByCoord[1].equalsIgnoreCase("ready")) {
                                            synchronized (coordinatorLock) {
                                                numberOfProcessReady++;
                                                synchronized (llc_lock){
                                                    int senderProcTS = Integer.parseInt(lineRecvdByCoord[0]);
                                                    int maxOfTS = Math.max(senderProcTS,llc_value);
                                                    llc_value=maxOfTS+1;
                                                    System.out.printf("<%d> ********ready frm : %s , # of procs ready : %d%n",llc_value,client.getInetAddress().getHostName(),numberOfProcessReady);
                                                }
                                            }
                                            while (true) {
                                                if (numberOfProcessReady == NUMBER_OF_PROCS) {
                                                    break;
                                                }
                                            }
                                            System.out.println();
                                            Thread.sleep(200);
                                            //modify below code to apprise the clients about their host id and neighbours
                                            synchronized (llc_lock){
                                                llc_value++;
                                                System.out.printf("<%d> ********sending compute to %s%n",llc_value,client.getInetAddress().getHostName());
                                                out.println(llc_value+",compute");
                                            }
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
                        System.out.println(e.getMessage());
                    }
                });
                coordinatorThread.start();
            }
        }

        //Running the non coordinator part of the process
        Thread initializingThread = new Thread(() -> {
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            //register with coordinator
            try {
                BufferedReader reader = new BufferedReader(new FileReader(CONFIG));
                String[] params = reader.readLine().split(" ");
                Socket client = new Socket(params[1], 5001);
                PrintWriter out = new PrintWriter(client.getOutputStream(), true);
                BufferedReader in = new BufferedReader(new InputStreamReader(client.getInputStream()));
                synchronized (llc_lock){
                    llc_value++;
                    out.println(llc_value+",register");
                    System.out.println();
                    System.out.printf("<%d> registration initiated%n",llc_value);
                    System.out.println();
                }

                String[] totalNumberOfProcs = reader.readLine().split(" ");
                int arraySize = Integer.parseInt(totalNumberOfProcs[3]);
                sendArray = new int[arraySize];
                recvArray = new int[arraySize];

                String line;
                while ((line = in.readLine()) != null) {
                    String[] parsedReceivedLine = line.split(",");
                    if (parsedReceivedLine[1].equals("registered")) {
                        System.out.println();
                        System.out.println("<i> registration success");
                        System.out.println();
                        synchronized (llc_lock){
                            int senderProcTS = Integer.parseInt(parsedReceivedLine[0]);
                            int maxOfTS = Math.max(senderProcTS,llc_value);
                            llc_value=maxOfTS+1;
                            System.out.printf("<%d> received PID and neighbour list from coordinator%n",llc_value);
                            PROCESSID = Integer.parseInt(parsedReceivedLine[2]);
                            System.out.printf("    PID : %d%n",PROCESSID);
                            System.out.printf("    Neighbours----------PID%n");
                            for (int i = 3; i < parsedReceivedLine.length; i++) {
                                String[] detailsOfNeighbour = parsedReceivedLine[i].split(" ");
                                localNeighbourSet.add(new Neighbour(detailsOfNeighbour[0],Integer.parseInt(detailsOfNeighbour[1])));
                                numberOfNeighbours++;
                                System.out.println("    "+detailsOfNeighbour[0]+"   "+detailsOfNeighbour[1]);
                            }
                            System.out.println("    -----------------------");
                            System.out.println();
                        }
                        synchronized (lock) {
                            canSendHello = true;
                        }
                    }
                    while (true) {
                        if (!blockingQueue.isEmpty())
                            break;
                    }
                    if (canSendReady) {
                        synchronized (llc_lock){
                            llc_value++;
                            System.out.println();
                            System.out.printf("<%d> sending ready to coordinator%n",llc_value);
                            System.out.println();
                            out.println(llc_value+",ready");
                        }
                        canSendReady = false;
                    }
                    if (parsedReceivedLine[1].equals("compute")) {
                        synchronized (lock) {
                            synchronized (llc_lock){
                                int senderProcTS = Integer.parseInt(parsedReceivedLine[0]);
                                int maxOfTS = Math.max(senderProcTS,llc_value);
                                llc_value=maxOfTS+1;
                                System.out.println();
                                System.out.printf("<%d> received compute from coordinator%n",llc_value);
                                System.out.println();
                            }
                            canSendCompute = true;
                        }
                    }
                }
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
        });

        Thread clientThreadMain = new Thread(() -> {
            while (true) {
                if (canSendHello)
                    break;
                try {
                    Thread.sleep(900);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }

            //for each neighbour, create new thread and establish new socket
            for (Neighbour n : localNeighbourSet) {
                Thread clientThreadAncillary = new Thread(() -> {
                    String line;
                    try {
                        Socket client = new Socket(n.getHostname(), 5000);
                        PrintWriter out = new PrintWriter(client.getOutputStream(), true);
                        BufferedReader in = new BufferedReader(new InputStreamReader(client.getInputStream()));
                        BufferedReader stdIn = new BufferedReader(new InputStreamReader(System.in));
                        synchronized (llc_lock){
                            llc_value++;
                            sendArray[n.getId()-1]++;
                            out.println(llc_value+" hello from PID "+PROCESSID);
                            System.out.printf("<%d> sending hello to PID %d%n",llc_value,n.getId());
                        }
                        while (true) {
                            if (canSendCompute) {
                                synchronized (llc_lock){
                                    llc_value++;
                                    sendArray[n.getId()-1]++;
                                    out.println(llc_value+" compute from PID " + PROCESSID);
                                }
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
                clientThreadAncillary.start();
            }
        });


        Thread serverThreadMain = new Thread(() -> {
            try {
                ServerSocket serverSocket = new ServerSocket(5000);
                while (true) {
                    Socket client = serverSocket.accept();
                    Thread serverThreadAncillary = new Thread(() -> {
                        try {
                            PrintWriter out = new PrintWriter(client.getOutputStream(), true);
                            BufferedReader in = new BufferedReader(new InputStreamReader(client.getInputStream()));
                            String line;
                            while ((line = in.readLine()) != null) {
                                String[] parsedLine = line.split(" ");
                                if (parsedLine[1].equalsIgnoreCase("hello")) {
                                    synchronized (llc_lock){
                                        int senderProcTS = Integer.parseInt(parsedLine[0]);
                                        int maxOfTS = Math.max(senderProcTS,llc_value);
                                        llc_value=maxOfTS+1;
                                        recvArray[Integer.parseInt(parsedLine[parsedLine.length-1])-1]++;
                                        System.out.printf("<%d> received hello from PID %d%n",llc_value,Integer.parseInt(parsedLine[parsedLine.length-1]));
                                    }
                                    synchronized (lock) {
                                        neighbourCounter++;
                                    }
                                    if (neighbourCounter == numberOfNeighbours)
                                        blockingQueue.add(1);
                                } else {
                                    synchronized (llc_lock){
                                        int senderProcTS = Integer.parseInt(parsedLine[0]);
                                        int maxOfTS = Math.max(senderProcTS,llc_value);
                                        llc_value=maxOfTS+1;
                                        recvArray[Integer.parseInt(parsedLine[parsedLine.length-1])-1]++;
                                        //for debugging send and recv arrays
                                        /*if(llc_value>100){
                                            //print send and recv array
                                            System.out.println("SEND ARRAY");
                                            for(int i : sendArray){
                                                System.out.printf("%d ",i);
                                            }
                                            System.out.println();
                                            System.out.println("RECV ARRAY");
                                            for(int i : recvArray){
                                                System.out.printf("%d ",i);
                                            }
                                            System.out.println();
                                        }*/
                                        System.out.printf("<%d> received compute from PID %d%n",llc_value,Integer.parseInt(parsedLine[parsedLine.length-1]));
                                    }
                                }
                            }
                            out.close();
                            in.close();
                            client.close();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    });
                    serverThreadAncillary.start();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        });

        initializingThread.start();
        clientThreadMain.start();
        serverThreadMain.start();


        try {
            initializingThread.join();
            clientThreadMain.join();
            serverThreadMain.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private static void runConfiguration(String fileLocation) {
        try {
            BufferedReader reader = new BufferedReader(new FileReader(fileLocation));
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parsedLines = line.split(" ");
                if (parsedLines[0].equalsIgnoreCase("COORDINATOR"))
                    continue;
                if (parsedLines[0].equalsIgnoreCase("NUMBER")) {
                    NUMBER_OF_PROCS = Integer.parseInt(parsedLines[3]);
                }
                if (parsedLines[0].equalsIgnoreCase("INTERVAL"))
                    continue;
                if (parsedLines[0].equalsIgnoreCase("TERMINATE"))
                    continue;
                if (parsedLines[0].equalsIgnoreCase("NEIGHBOR")) {
                    while ((line = reader.readLine()) != null) {
                        if (line.equals("")) {
                            break;
                        }
                        String[] arrayOfProcesses = line.split(" ");
                        Set<Integer> setOfNeighbourProcesses = new HashSet<>();
                        for (int i = 1; i < arrayOfProcesses.length; i++) {
                            setOfNeighbourProcesses.add(Integer.parseInt(arrayOfProcesses[i]));
                        }
                        neighbours.put(Integer.parseInt(arrayOfProcesses[0]), setOfNeighbourProcesses);
                    }
                }
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}

/*
class Neighbour {
    private int id;
    private String hostname;
    private String portnum;

    public Neighbour(){}

    public Neighbour(String hostname,int id){
        this.hostname=hostname;
        this.id=id;
    }

    public Neighbour(String hostname){
        this.hostname=hostname;
    }
    public Neighbour(String hostname, String portnum) {
        this.hostname = hostname;
        this.portnum = portnum;
    }

    public Neighbour(int id, String hostname, String portnum) {
        this.id = id;
        this.hostname = hostname;
        this.portnum = portnum;
    }

    public String getHostname() {
        return hostname;
    }

    public void setHostname(String hostname) {
        this.hostname = hostname;
    }

    public String getPortnum() {
        return portnum;
    }

    public void setPortnum(String portnum) {
        this.portnum = portnum;
    }

    public int getId() {
        return id;
    }
}
*/