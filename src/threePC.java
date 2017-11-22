import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

public class threePC {

    private static final int NUMBER_OF_PROCS = 3;

    private static final int PORT = 5000;
    private static final String ADDRESS = "net01.utdallas.edu";


    private static Object writeLock = new Object();

    private static volatile AtomicInteger numberOfRegCohorts = new AtomicInteger(0);
    private static volatile AtomicInteger numberOfProcsWrittenTo = new AtomicInteger(0);
    private static volatile AtomicInteger numberOfAcksToComReq = new AtomicInteger(0);
    private static volatile AtomicInteger numberOfAcksToPrepareCom = new AtomicInteger(0);
    private static volatile AtomicInteger numberOfAcksToActualCom = new AtomicInteger(0);


    private static volatile boolean write = false;
    private static volatile boolean commitReq = false;
    private static volatile boolean prepareComm = false;
    private static volatile boolean actualComm = false;
    private static volatile StringBuilder valueToBeWritten;
    private static volatile BufferedWriter stateWriter;
    private static int commitableVal;


    public static void main(String[] args) {

        if (args.length != 0) {
            if (args[0].equalsIgnoreCase("-c")) {
                Thread coordinatorThread = new Thread(() -> {

                    Thread coordinatorEngineThread = new Thread(() -> {
                        while(true){
                            if(numberOfRegCohorts.get()==NUMBER_OF_PROCS)
                                break;
                            else
                                continue;
                        }

                        System.out.println("*************COHORT REGISTRATION COMPLETE");

                        valueToBeWritten = new StringBuilder();
                        valueToBeWritten.append("CRQ;");
                        write=true;

                        while(true){
                            if(numberOfAcksToComReq.get()==NUMBER_OF_PROCS)
                                break;
                            else
                                continue;
                        }

                        System.out.println("*************COHORT COMMIT REQ COMPLETE");

                        valueToBeWritten = new StringBuilder();
                        valueToBeWritten.append("PCM;");
                        write=true;

                        while(true){
                            if(numberOfAcksToPrepareCom.get()==NUMBER_OF_PROCS)
                                break;
                            else
                                continue;
                        }

                        System.out.println("*************COHORT PREPARE COMMIT COMPLETE");


                        Scanner scanner = new Scanner(System.in);
                        System.out.print("Commit value : ");


                        valueToBeWritten = new StringBuilder();
                        valueToBeWritten.append("COM;"+scanner.nextLine());
                        write=true;

                        while(true){
                            if(numberOfAcksToActualCom.get()==NUMBER_OF_PROCS)
                                break;
                            else
                                continue;
                        }

                        System.out.println("*************COHORT COMMIT COMPLETE");
                        System.exit(0);

                    });
                    coordinatorEngineThread.start();

                    try {
                        ServerSocket serverSocket = new ServerSocket(PORT);
                        //System.out.println("starting main coordinator thread...");
                        while (true) {
                            Socket client = serverSocket.accept();
                            Thread handlerThread = new Thread(() -> {
                                try {
                                    PrintWriter out = new PrintWriter(client.getOutputStream(), true);
                                    BufferedReader in = new BufferedReader(new InputStreamReader(client.getInputStream()));
                          //          System.out.println("waiting...");
                                    Thread readThread = new Thread(()->{
                                        String line;
                                        try {
                                            while ((line = in.readLine()) != null) {
                                                String[] parsedLine = line.split(";");

                                                if(parsedLine[0].equalsIgnoreCase("reg")){
                                                    System.out.println("register cohort : "+parsedLine[1]);
                                                    numberOfRegCohorts.incrementAndGet();
                                                }

                                                if(parsedLine[0].equalsIgnoreCase("acrq")){
                                                    System.out.println("ack to com req received from : "+parsedLine[1]);
                                                    numberOfAcksToComReq.incrementAndGet();
                                                }

                                                if(parsedLine[0].equalsIgnoreCase("apcm")){
                                                    System.out.println("ack to prepare comm received from : "+parsedLine[1]);
                                                    numberOfAcksToPrepareCom.incrementAndGet();
                                                }

                                                if(parsedLine[0].equalsIgnoreCase("acom")){
                                                    System.out.println("ack to actual comm received from : "+parsedLine[1]);
                                                    numberOfAcksToActualCom.incrementAndGet();
                                                }

                                            }
                                        } catch (IOException e) {
                                            e.printStackTrace();
                                        }
                                    });
                                    Thread writeThread = new Thread(()->{
                                        //some more task
                                        while (true){
                                            if(write){
                                                synchronized (writeLock){
                                                    out.println(valueToBeWritten.toString());
                                                    numberOfProcsWrittenTo.incrementAndGet();
                                                    if(numberOfProcsWrittenTo.get()==NUMBER_OF_PROCS){
                                                        write=false;
                                                        numberOfProcsWrittenTo.set(0);
                                                    }
                                                }
                                                try {
                                                    Thread.sleep(100);
                                                } catch (InterruptedException e) {
                                                    e.printStackTrace();
                                                }
                                            }
                                        }

                                    });
                                    readThread.start();
                                    writeThread.start();
                                    readThread.join();
                                    writeThread.join();
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
                        }
                    } catch (IOException e) {
                        System.out.println(e.getMessage());
                    }
                });
                coordinatorThread.start();
            } else {
                Thread clientThreadMain = new Thread(() -> {

                    Thread cohortEngine = new Thread(()->{

                        File file = new File("state_file_"+args[0]);
                        if(!file.exists()){
                            createFileAndWriter(file);
                        } else {
                            loadFileAndWriter(file);
                        }

                        persistStateToFile(stateWriter,"q");

                        while (true){
                            if(commitReq)
                                break;
                        }

                        persistStateToFile(stateWriter,"w");

                        prepareToWriteToCoordinator("ACRQ;"+args[0]);

                        while (true){
                            if(prepareComm)
                                break;
                        }

                        persistStateToFile(stateWriter,"p");

                        prepareToWriteToCoordinator("APCM;"+args[0]);

                        while (true){
                            if(actualComm)
                                break;
                        }

                        persistStateToFile(stateWriter,String.valueOf(commitableVal));

                        prepareToWriteToCoordinator("ACOM;"+args[0]);

                        System.exit(0);

                    });
                    cohortEngine.start();

                    try {
                        Socket client = new Socket(ADDRESS, PORT);
                        PrintWriter out = new PrintWriter(client.getOutputStream(), true);
                        BufferedReader in = new BufferedReader(new InputStreamReader(client.getInputStream()));
                        Thread readThread = new Thread(()->{
                            String line;
                            try {
                                while ((line = in.readLine()) != null) {
                                    String[] parsedLine = line.split(";");

                                    if(parsedLine[0].equalsIgnoreCase("crq")){
                                        System.out.println("commit req received");
                                        commitReq = true;
                                    }

                                    if(parsedLine[0].equalsIgnoreCase("pcm")){
                                        System.out.println("prepare commit received");
                                        prepareComm = true;
                                    }

                                    if(parsedLine[0].equalsIgnoreCase("com")){
                                        System.out.println("committing value  "+parsedLine[1]);
                                        commitableVal = Integer.parseInt(parsedLine[1]);
                                        actualComm = true;
                                    }
                                }
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        });
                        Thread writeThread = new Thread(()->{
                            out.println("REG;"+args[0]);
                            while (true){
                                if(write){
                                    out.println(valueToBeWritten.toString());
                                    write=false;
                                }
                            }
                        });
                        readThread.start();
                        writeThread.start();
                        readThread.join();
                        writeThread.join();
                        out.close();
                        in.close();
                    } catch (UnknownHostException e) {
                        e.printStackTrace();
                    } catch (FileNotFoundException e) {
                        e.printStackTrace();
                    } catch (IOException e) {
                        e.printStackTrace();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                });
                clientThreadMain.start();
                try {
                    clientThreadMain.join();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private static void prepareToWriteToCoordinator(String message) {
        valueToBeWritten = new StringBuilder();
        valueToBeWritten.append(message);
        write=true;
    }

    private static void loadFileAndWriter(File file) {
        //load file
        try {
            stateWriter = new BufferedWriter(new FileWriter(file.getName(),true));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void createFileAndWriter(File file) {
        try {
            file.createNewFile();
            stateWriter = new BufferedWriter(new FileWriter(file.getName(),true));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void persistStateToFile(BufferedWriter stateWriter, String state) {
        try {
            stateWriter.write(state);
            stateWriter.newLine();
            stateWriter.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}