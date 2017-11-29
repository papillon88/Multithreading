import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class threePC {

    private static final int NUMBER_OF_PROCS = 4;

    private static final int PORT = 5000;
    private static final String ADDRESS = "net01.utdallas.edu";

    private static volatile AtomicInteger numberOfRegCohorts = new AtomicInteger(0);
    private static volatile AtomicInteger numberOfProcsWrittenTo = new AtomicInteger(0);
    private static volatile AtomicInteger numberOfAcksToComReq = new AtomicInteger(0);
    private static volatile AtomicInteger numberOfAcksToPrepareCom = new AtomicInteger(0);
    private static volatile AtomicInteger numberOfAcksToActualCom = new AtomicInteger(0);

    private static volatile Object writeLock = new Object();
    private static volatile boolean write = false;
    private static volatile boolean commitReq = false;
    private static volatile boolean prepareComm = false;
    private static volatile boolean actualComm = false;
    private static volatile StringBuilder valueToBeWritten;
    private static volatile BufferedWriter stateWriter;
    private static volatile int commitableVal;
    private static volatile String suppliedProcessToFail;
    private static volatile boolean abort = false;


    private static volatile int timeOutTimer = 0;
    private static volatile boolean transactionAborted = false;
    private static volatile int testCase = 0;
    private static volatile int transactionId = 0;
    private static volatile int majorTransactionId = 0;


    public static void main(String[] args) {

        if (args.length != 0) {
            if (args[0].equalsIgnoreCase("co")) {

                Thread coordinatorProcessorThread = new Thread(() -> {
                    while(true){
                        if(numberOfRegCohorts.get()>=NUMBER_OF_PROCS)
                            break;
                    }

                    //this is a one time registration
                    System.out.println("*************COHORT REGISTRATION COMPLETE");

                    while (true){

                        Scanner scanner = new Scanner(System.in);
                        System.out.print("Commit value : ");
                        String[] scannedVal = scanner.nextLine().split(" ");
                        suppliedProcessToFail = scannedVal[2];
                        testCase = Integer.valueOf(scannedVal[1]);

                        valueToBeWritten = new StringBuilder();
                        valueToBeWritten.append("CRQ");valueToBeWritten.append(";");
                        valueToBeWritten.append(String.valueOf(majorTransactionId++));valueToBeWritten.append(";");
                        valueToBeWritten.append(scannedVal[0]);valueToBeWritten.append(";");//value to commit or abort
                        valueToBeWritten.append(scannedVal[1]);valueToBeWritten.append(";");//test case
                        valueToBeWritten.append(scannedVal[2]);valueToBeWritten.append(";");//process to fail
                        write=true;

                        while(true){
                            try {
                                Thread.sleep(300);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                            if(numberOfAcksToComReq.get()==NUMBER_OF_PROCS){
                                //System.out.println("got 3 acks to comm reqs");
                                transactionAborted = false;
                                timeOutTimer = 0;
                                break;
                            } else {
                                if(timeOutTimer == 10){
                                    timeOutTimer = 0;
                                    transactionAborted = true;
                                    numberOfAcksToComReq.set(0);
                                    //System.out.println("time out : setting numberofackstocommreq to 0"+numberOfAcksToComReq.get());

                                    valueToBeWritten = new StringBuilder();
                                    valueToBeWritten.append("ABT");valueToBeWritten.append(";");
                                    write=true;
                                    break;
                                } else {
                                    timeOutTimer++;
                                }
                            }
                        }

                        if(transactionAborted)
                            continue;

                        System.out.println("*************COHORT COMMIT REQ COMPLETE");

                        //coordinator fails after W state but before P state
                        if(testCase == 3 & suppliedProcessToFail.equalsIgnoreCase(args[0])){
                            System.out.println(args[0]+" fails");
                            System.exit(0);
                        }

                        valueToBeWritten = new StringBuilder();
                        valueToBeWritten.append("PCM");valueToBeWritten.append(";");
                        write=true;

                        while(true){
                            try {
                                Thread.sleep(300);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                            if(numberOfAcksToPrepareCom.get()==NUMBER_OF_PROCS){
                                transactionAborted = false;
                                timeOutTimer = 0;
                                break;
                            } else {
                                if(timeOutTimer == 10){
                                    timeOutTimer = 0;
                                    transactionAborted = true;
                                    numberOfAcksToComReq.set(0);
                                    numberOfAcksToPrepareCom.set(0);

                                    valueToBeWritten = new StringBuilder();
                                    valueToBeWritten.append("ABT");valueToBeWritten.append(";");
                                    write=true;
                                    break;
                                } else {
                                    timeOutTimer++;
                                    continue;
                                }
                            }
                        }

                        if(transactionAborted)
                            continue;

                        System.out.println("*************COHORT PREPARE COMMIT COMPLETE");

                        //coordinator fails after P state but before C state
                        //if coordinator doesnt send a commit here, fails, or times out at this point, the cohorts commit anyway
                        //after their timeouttimes expires as they are already in the prepared state !
                        if(testCase == 4 & suppliedProcessToFail.equalsIgnoreCase(args[0])){
                            System.out.println(args[0]+" fails");
                            System.exit(0);
                        }

                        valueToBeWritten = new StringBuilder();
                        valueToBeWritten.append("COM");valueToBeWritten.append(";");
                        valueToBeWritten.append(scannedVal[0]);valueToBeWritten.append(";");
                        write=true;

                        System.out.println("sending actual commit");

                        while(true){
                            if(numberOfAcksToActualCom.get()==NUMBER_OF_PROCS)
                                break;
                            else
                                continue;
                        }

                        System.out.println("*************COHORT COMMIT COMPLETE");
                        numberOfAcksToComReq.set(0);
                        numberOfAcksToPrepareCom.set(0);
                        numberOfAcksToActualCom.set(0);
                    }
                });
                coordinatorProcessorThread.start();

                Thread coordinatorMainThread = new Thread(() -> {
                    try {
                        ServerSocket serverSocket = new ServerSocket(PORT);
                        //System.out.println("starting main coordinator thread...");
                        while (true) {
                            Socket client = serverSocket.accept();

                            Thread handlerThread = new Thread(() -> {
                                try {
                                    PrintWriter out = new PrintWriter(client.getOutputStream(), true);
                                    BufferedReader in = new BufferedReader(new InputStreamReader(client.getInputStream()));

                                    AtomicInteger id = new AtomicInteger(0);
                                    AtomicInteger timer = new AtomicInteger(0);
                                    AtomicBoolean clientIsDead = new AtomicBoolean(false);
                                    AtomicBoolean readExit = new AtomicBoolean(false);
                                    AtomicBoolean writeExit = new AtomicBoolean(false);

                                    Thread readThread = new Thread(()->{
                                        String line;
                                        try {
                                            while ((line = in.readLine()) != null & readExit.get() != true) {
                                                String[] parsedLine = line.split(";");

                                                if(parsedLine[0].equalsIgnoreCase("reg")){
                                                    System.out.println("register cohort : "+parsedLine[1]);
                                                    System.out.println("setting interenal process ID to " + parsedLine[1]);
                                                    id.set(Integer.valueOf(parsedLine[1].substring(1)));
                                                    numberOfRegCohorts.incrementAndGet();
                                                }

                                                if(parsedLine[0].equalsIgnoreCase("acrq")){
                                                    System.out.println("ack to com req received from : "+parsedLine[1]);
                                                    numberOfAcksToComReq.incrementAndGet();
                                                    //System.out.println(numberOfAcksToComReq.toString());
                                                }

                                                if(parsedLine[0].equalsIgnoreCase("apcm")){
                                                    System.out.println("ack to prepare comm received from : "+parsedLine[1]);
                                                    numberOfAcksToPrepareCom.incrementAndGet();
                                                }

                                                if(parsedLine[0].equalsIgnoreCase("acom")){
                                                    System.out.println("ack to actual comm received from : "+parsedLine[1]);
                                                    numberOfAcksToActualCom.incrementAndGet();
                                                }

                                                if(parsedLine[0].equalsIgnoreCase("pong")){
                                                    //System.out.println("pong from "+id.get());
                                                    clientIsDead.set(false);
                                                }

                                            }
                                        } catch (IOException e) {
                                            e.printStackTrace();
                                        }
                                    });
                                    Thread writeThread = new Thread(()->{
                                        //some more task
                                        while (!writeExit.get()){
                                            if(write){
                                                synchronized (writeLock){
                                                    out.println(valueToBeWritten.append(id).toString());
                                                    System.out.println(valueToBeWritten.toString());
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


                                    while (true){
                                        //System.out.println("ping to "+id.get());
                                        out.println("ping;");
                                        clientIsDead.set(true);
                                        try {
                                            Thread.sleep(1000);
                                        } catch (InterruptedException e) {
                                            e.printStackTrace();
                                        }
                                        if(clientIsDead.get()){
                                            if(timer.get() == 3){
                                                //System.out.println("interrupting thread...");
                                                readExit.set(true);
                                                writeExit.set(true);
                                                break;
                                            } else {
                                                System.out.println("waiting for cohort ...");
                                                timer.incrementAndGet();
                                            }
                                        }
                                    }


                                    readThread.join();
                                    writeThread.join();

                                    System.out.println("closing this thread coz the cohort is dead ...");

                                    out.close();
                                    in.close();
                                    client.close();
                                } catch (IOException e) {
                                    e.printStackTrace();
                                } catch (InterruptedException e) {
                                    e.getMessage();
                                }
                            });
                            handlerThread.start();
                        }
                    } catch (IOException e) {
                        System.out.println(e.getMessage());
                    }
                });
                coordinatorMainThread.start();

                try {
                    coordinatorMainThread.join();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

            } else {

                Thread cohortProcessorThread = new Thread(()->{

                    while (true){

                        try {
                            Thread.sleep(300);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }

                        File file = new File("state_file_"+args[0]);
                        if(!file.exists()){
                            createFileAndWriter(file);
                        } else {
                            loadFileAndWriter(file);
                        }

                        while (true){
                            if(commitReq){
                                if(!abort){
                                    persistStateToFile(stateWriter,"q "+String.valueOf(commitableVal)+" "+String.valueOf(transactionId));
                                    System.out.println("persisting Q to state file");
                                }
                                break;
                            }
                        }commitReq = false;

                        if(testCase == 1 & suppliedProcessToFail.equalsIgnoreCase(args[0])){
                            System.out.println(args[0]+" fails");
                            System.exit(0);
                        }


                        if(!abort){
                            persistStateToFile(stateWriter,"w "+String.valueOf(commitableVal)+" "+String.valueOf(transactionId));
                            System.out.println("persisting W to state file");

                            valueToBeWritten = new StringBuilder();
                            valueToBeWritten.append("ACRQ");valueToBeWritten.append(";");
                            valueToBeWritten.append(args[0]);valueToBeWritten.append(";");
                            write = true;
                        }

                        while(true){
                            try {
                                Thread.sleep(300);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                            if(prepareComm){
                                timeOutTimer = 0;
                                break;
                            } else {
                                if(timeOutTimer == 10){
                                    timeOutTimer = 0;
                                    System.out.println("time out ...");
                                    System.out.println("aborting now ...");
                                    actualComm = true;
                                    abort = true;
                                    break;
                                } else {
                                    timeOutTimer++;
                                    System.out.println("waiting for prepare commit from coordinator ..."+timeOutTimer);
                                }
                            }
                        }prepareComm = false;

                        if(testCase == 2 & suppliedProcessToFail.equalsIgnoreCase(args[0])){
                            System.out.println(args[0]+" fails");
                            System.exit(0);
                        }

                        if(!abort){
                            persistStateToFile(stateWriter,"p "+String.valueOf(commitableVal)+" "+String.valueOf(transactionId));
                            System.out.println("persisting P to state file");

                            valueToBeWritten = new StringBuilder();
                            valueToBeWritten.append("APCM");valueToBeWritten.append(";");
                            valueToBeWritten.append(args[0]);valueToBeWritten.append(";");
                            write = true;
                        }

                        while(true){
                            try {
                                Thread.sleep(300);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                            if(actualComm){
                                timeOutTimer = 0;
                                break;
                            } else {
                                if(timeOutTimer == 10){
                                    timeOutTimer = 0;
                                    System.out.println("time out ...");
                                    System.out.println("commit now ...");
                                    break;
                                } else {
                                    timeOutTimer++;
                                    System.out.println("waiting for actual commit from coordinator ..."+timeOutTimer);
                                }
                            }
                        }actualComm = false;

                        if(!abort) {
                            System.out.println("persisting C to state file");
                            persistStateToFile(stateWriter,"c "+String.valueOf(commitableVal)+" "+String.valueOf(transactionId));
                        } else {
                            System.out.println("persisting A to state file");
                            persistStateToFile(stateWriter,"a "+String.valueOf(commitableVal)+" "+String.valueOf(transactionId));
                        }


                        File finalRoundUpfile = new File("tx_ro_"+args[0]);
                        if(!finalRoundUpfile.exists()){
                            try {
                                finalRoundUpfile.createNewFile();
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }

                        try {
                            BufferedWriter finalWriter = new BufferedWriter(new FileWriter(finalRoundUpfile.getName(),true));
                            if(!abort)
                                finalWriter.write(transactionId+"   COMMIT  "+commitableVal);
                            else
                                finalWriter.write(transactionId+"   ABORT  "+commitableVal);
                            finalWriter.newLine();
                            finalWriter.flush();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }


                        if(!abort){
                            valueToBeWritten = new StringBuilder();
                            valueToBeWritten.append("ACOM");valueToBeWritten.append(";");
                            valueToBeWritten.append(args[0]);valueToBeWritten.append(";");
                            write = true;
                        }

                        //delete staging file
                        while (true){
                            if(!abort)
                                break;
                        }abort = false;

                        System.out.println("before deleting");
                        file.delete();
                        System.out.println("after deleting");
                        try {
                            Thread.sleep(1000);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }

                });
                cohortProcessorThread.start();

                Thread cohortMainThread = new Thread(() -> {
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
                                        transactionId = Integer.parseInt(parsedLine[1]);
                                        commitableVal = Integer.parseInt(parsedLine[2]);
                                        testCase = Integer.parseInt(parsedLine[3]);
                                        suppliedProcessToFail = parsedLine[4];
                                        commitReq = true;
                                        prepareComm = false;
                                        actualComm = false;
                                        abort = false;
                                    }

                                    if(parsedLine[0].equalsIgnoreCase("pcm")){
                                        System.out.println("prepare commit received");
                                        prepareComm = true;
                                    }

                                    if(parsedLine[0].equalsIgnoreCase("com")){
                                        System.out.println("committing value  "+parsedLine[1]);
                                        actualComm = true;
                                    }

                                    if(parsedLine[0].equalsIgnoreCase("abt")){
                                        System.out.println("aborting value  "+commitableVal);
                                        abort = true;
                                        prepareComm = true;
                                        actualComm = true;
                                    }

                                    if(parsedLine[0].equalsIgnoreCase("ping")){
                                        //System.out.println("ping received");
                                        out.println("pong;");
                                    }
                                }
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        });
                        Thread writeThread = new Thread(()->{
                            File haltFile = new File("state_file_"+args[0]);
                            if(!haltFile.exists())
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
                cohortMainThread.start();

                try {
                    cohortMainThread.join();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }


    }

    private static void loadFileAndWriter(File file) {
        //load file
        try {
            BufferedReader reader = new BufferedReader(new FileReader(file.getName()));
            List<String> list = new ArrayList<>();
            String line;
            while ((line = reader.readLine()) != null) {
                list.add(line);
            }
            String[] checkPoint = list.get(list.size() - 1).split(" ");
            if(checkPoint[0].equalsIgnoreCase("q")){
                commitableVal = Integer.parseInt(checkPoint[1]);
                transactionId = Integer.parseInt(checkPoint[2]);
                commitReq = true;
                prepareComm = true;
                actualComm = true;
                suppliedProcessToFail = "tamarind";
                abort = true;
            }
            if(checkPoint[0].equalsIgnoreCase("w")){
                commitableVal = Integer.parseInt(checkPoint[1]);
                transactionId = Integer.parseInt(checkPoint[2]);
                commitReq = true;
                prepareComm = true;
                actualComm = true;
                suppliedProcessToFail = "tamarind";
                abort = true;
            }
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