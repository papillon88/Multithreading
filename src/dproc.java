/**
 *
 *      A PRIMER TO THE PROGRAM

        Abbreviations used :
        coordinator process = CP
        non-coordinator process = NCP
        distributed system = DS
        process id = PID

        Messages exchanged between procs, like register, registered, compute, hello etc
        are basically strings separated by ,(comma) and combined into a bigger string and
        then sent across. Format of the final message thats sent across is as follows :
        A,B,C,D,E,......
        where A = timestamp : eg <3>, <4> etc
        where B = message type (register,ready,compute etc)
        where C = sender PID
        where D,E,F.... etc is the rest of the message
                            whose content depends on the type of the message sent



        The program works on a privately connected network
        where public domains are not involved and where the machines
        use a shared file system.
        It can be run as a CP or a NCP : depending on whether
        "-c" flag is passed or otherwise respectively.

        Procedure : Compile the file on one machine(javac dproc.java). Because the
        file system is shared, compiled file is available across
        the network. Based on what the coordinator hostname is in the
        dsConfig file, run "java dproc -c" on that particular machine to start the CP.
        Run "java dproc" on the other machines to enable the NCPs. The DS
        will boot up, nodes talk to each other by sending compute msgs,
        in parallel snapshot algo is run and finally the system is terminated
        after the final snapshot is recorded.

        NOTE :  The CP process runs all the following threads(including threads for the non-coordinator).
                The NCP process runs only the threads meant for itself(not the coordinator threads).

        The coordinator part of the program contains the
        following 3 important parts :
            1)  runConfiguration(CONFIG) method that parses the dsConfig
                file. NOTE : CONFIG file dsConfig should be located in the
                same directroy as the dproc.java file.
            2)  coordinator thread : this main coordinator thread
                spawns many sub-coordinator threads ( named as
                coordinator ancillary) whose number depends on
                the number of connecting nodes. When a node connects,
                a new TCP socket is formed and a new cooordinator ancillary
                thread is created to handle incoming traffic from node.
            3)  chandyLamportInit thread that regularly checks the lamport
                logical clock valuellc_value (abbreviated as llc_value in the program)
                against the monotonically increasing repeater value whose value
                increments by INTERVAL value mentioned in the dsConfig file. When increment
                finally crosses the TIMEOUT value from dsConfig, a final marker with value
                0 is sent. Recieveing processes after recieving this final marker,
                record their states for the final time and then shut down all message flows.

        The non coordinator part of the program contains the
        following important parts :
            1)  initializingThread enables the NCPs to register with the CP,
                receive neighbour list(hostnames) and process id from the CP.
            2)  clientThreadMain enables the NCPs and CP to send messages to each other
                using hello and compute messages. This main thread spawns sub threads
                (client ancillary) that shoot connection requests to each of its neighbour.
                Each client ancillary thread boots a forwardMarker Thread that takes care
                of forwarding marker messages to neighbours.
            3)  serverThreadMain enables NCPs and CP to receive messages from each other.
                This main thread spawns sub threads(server ancillary) to handle each of
                those incoming requests and messages.
 */

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

public class dproc {

    //config file must be present in same directory as this java file after compilation
    static final String CONFIG = "dsConfig";

    //default ports
    static int CP_PORT = 5001;
    static int NCP_PORT = 5000;

    //Files and i/o related
    static BufferedWriter stateWriter;

    //RANDOMIZING ELEMENT
    static final SecureRandom random = new SecureRandom();


    //CLOCK
    //timestamp lamport logical clock value
    static volatile int llc_value= 0;
    //lock for the clock
    static Object llc_lock = new Object();


    //MARKER
    static final int FINAL_MARKER = 0;
    static volatile boolean forwardMarker = false;
    static Object markerLock = new Object();
    static volatile boolean firstMarkerReceived = false;
    static volatile int resetMarkerCounter = 0;
    static Object yetAnotherMarkerLock = new Object();
    static volatile int neighbourMarkerCounter = 0;
    static volatile int recordingState = 0;
    static volatile ArrayBlockingQueue<Integer> mrkQueue = new ArrayBlockingQueue<Integer>(300);
    static volatile ArrayBlockingQueue<Integer> pidQueue = new ArrayBlockingQueue<Integer>(300);


    //COORDINATOR PROCESS PARAMS
    static boolean coordinator = false;
    static int NUMBER_OF_PROCS;
    static volatile int numberOfProcessRegistered = 0;
    static volatile int numberOfProcessReady = 0;
    static Object coordinatorLock = new Object();
    static Map<Integer, Set<Integer>> neighbours = new HashMap<>();
    static Map<Integer, Neighbour> pidToHostnameMap = new HashMap<>();
    static volatile int[][] channelMatrix;
    static volatile Record[] recordsBook = new Record[500];
    static int PROCESSID;
    static int INTERVAL;
    static int TERMINATE;


    //NON COORDINATOR PROCESS PARAMS
    static volatile boolean canSendHello = false;
    static volatile boolean canSendReady = true;
    static volatile boolean canSendCompute = false;
    static volatile boolean closeAllComputes = false;
    static BlockingQueue<Integer> blockingQueue = new ArrayBlockingQueue<>(1);
    static volatile int numberOfNeighbours = 0;
    static volatile int neighbourCounter = 0;
    static Object lock = new Object();
    static Set<Neighbour> localNeighbourSet = new HashSet<>();
    static volatile int[] sendArray;
    static volatile int[] recvArray;
    static volatile int[] chnlArray;


    public static void main(String[] args) {

        Scanner scanner = new Scanner(System.in);
        System.out.print("Enter coordinator port number or hit ENTER to use default(5001) : ");
        String cpString = scanner.nextLine();
        if(!cpString.equalsIgnoreCase(""))
            CP_PORT=Integer.parseInt(cpString);
        System.out.print("Enter non coordinator port number or hit ENTER to use default(5000) : ");
        String ncpString = scanner.nextLine();
        if(!ncpString.equalsIgnoreCase(""))
            NCP_PORT=Integer.parseInt(cpString);


        if (args.length != 0) {
            if (args[0].equalsIgnoreCase("-c")) {

                System.out.println("CP server running on port : "+CP_PORT);
                System.out.println("NCP listener server running on port : "+NCP_PORT);
                //Running the coordinator part of the process

                //set this proc as a CP
                coordinator = true;

                //read dsConfig and parse values into in memory variables for later use
                runConfiguration(CONFIG);


                //The main coordinator thread
                Thread coordinatorThread = new Thread(() -> {
                    System.out.println();
                    System.out.println("<i> ********Coordinator process initiated");
                    System.out.println("<i> ********Waiting for processes to register...");
                    System.out.println();
                    try {
                        ServerSocket serverSocket = new ServerSocket(CP_PORT);
                        while (true) {
                            Socket client = serverSocket.accept();

                            //each connection request to the coordinator thread spawns separate ancillary thread
                            Thread coordinatorAncillary = new Thread(() -> {
                                try {
                                    PrintWriter out = new PrintWriter(client.getOutputStream(), true);
                                    BufferedReader in = new BufferedReader(new InputStreamReader(client.getInputStream()));
                                    String line;
                                    int pid;
                                    //listen for incoming messages from the other end of the TCP pipe
                                    //incoming message format is A,B,C,D......
                                    //where A = timestamp
                                    //where B = message type (register,ready etc)
                                    //where C = sender PID
                                    //where D and other are the rest of the message
                                    while ((line = in.readLine()) != null) {

                                        //parse the message
                                        String[] lineRecvdByCoord = line.split(",");

                                        //if parsed message is register, then do this
                                        if (lineRecvdByCoord[1].equalsIgnoreCase("register")) {
                                            synchronized (coordinatorLock) {
                                                numberOfProcessRegistered++;
                                                pid = numberOfProcessRegistered;
                                                pidToHostnameMap.put(pid,
                                                        new Neighbour(String.valueOf(client.getInetAddress().getHostName()),pid));
                                                //lamport logical clock
                                                synchronized (llc_lock){
                                                    int senderProcTS = Integer.parseInt(lineRecvdByCoord[0]);
                                                    int maxOfTS = Math.max(senderProcTS,llc_value);
                                                    llc_value=maxOfTS+1;
                                                    System.out.printf("<%d> ********register frm : %s , # of procs registered : %d%n",llc_value,client.getInetAddress().getHostName(),numberOfProcessRegistered);
                                                }
                                            }

                                            //wait for all procs to register
                                            while (true) {
                                                if (numberOfProcessRegistered == NUMBER_OF_PROCS) {
                                                    break;
                                                }
                                            }

                                            //post registration , build in memory maps and sets for registered procs and their neighbour lists
                                            System.out.println();
                                            Thread.sleep(200);

                                            //modify below code to apprise the clients about their host id and neighbours
                                            StringBuilder stringBuilder = new StringBuilder();
                                            stringBuilder.append(pid);
                                            Set<Integer> tempSet = neighbours.get(pid);
                                            for (Integer i : tempSet) {
                                                stringBuilder.append(",");
                                                Neighbour neighbour = pidToHostnameMap.get(i);
                                                stringBuilder.append(neighbour.getHostname()+":"+neighbour.getId());
                                            }

                                            //send registered message to procs along with their PIDs and neighbour lists
                                            //lamport logical clock
                                            synchronized (llc_lock){
                                                llc_value++;
                                                out.println(llc_value+",registered," + stringBuilder.toString());
                                                System.out.printf("<%d> ********sending registered to %s%n",llc_value,client.getInetAddress().getHostName());
                                            }
                                        }


                                        //if parsed message is ready, then do this
                                        if (lineRecvdByCoord[1].equalsIgnoreCase("ready")) {
                                            synchronized (coordinatorLock) {
                                                numberOfProcessReady++;
                                                //lamport logical clock
                                                synchronized (llc_lock){
                                                    int senderProcTS = Integer.parseInt(lineRecvdByCoord[0]);
                                                    int maxOfTS = Math.max(senderProcTS,llc_value);
                                                    llc_value=maxOfTS+1;
                                                    System.out.printf("<%d> ********ready frm PID# : %s , # of procs ready : %d%n",llc_value,lineRecvdByCoord[2],numberOfProcessReady);
                                                }
                                            }

                                            //wait for all procs to get ready
                                            while (true) {
                                                if (numberOfProcessReady == NUMBER_OF_PROCS) {
                                                    break;
                                                }
                                            }
                                            System.out.println();
                                            Thread.sleep(200);

                                            //send compute to all procs once ready is received from all procs
                                            //lamport logical clock
                                            synchronized (llc_lock){
                                                llc_value++;
                                                System.out.printf("<%d> ********sending compute to %s%n",llc_value,client.getInetAddress().getHostName());
                                                out.println(llc_value+",compute,"+PROCESSID);
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
                            coordinatorAncillary.start();
                            //handlerThread.join();
                        }
                    } catch (IOException e) {
                        System.out.println(e.getMessage());
                    }
                });
                coordinatorThread.start();


                //coordinator invokes chandy lamport at regular INTERVAL till TIMEOUT
                //separate thread because compute needs to happen parallely to snapshotting
                Thread chandyLamportInit = new Thread(()->{

                    channelMatrix = new int[500][NUMBER_OF_PROCS];
                    int repeater = INTERVAL;

                    while(true){
                        if(llc_value>repeater){
                            recordingState++;
                            Record record = new Record(recordingState,sendArray,recvArray);
                            recordsBook[recordingState]=record;
                            System.out.println("STARTING RECORDING STATE : "+recordingState);
                            System.out.println("SEND : "+Arrays.toString(record.getSend()));
                            System.out.println("RECV : "+Arrays.toString(record.getRecv()));
                            System.arraycopy(record.getRecv(),0,channelMatrix[record.getRecordingState()],0,recvArray.length);
                            forwardMarker=true;

                            //final round of snapshot
                            if(repeater>TERMINATE){
                                //snapshot for the last time
                                recordingState=FINAL_MARKER;
                                record = new Record(recordingState,sendArray,recvArray);
                                recordsBook[recordingState]=record;
                                System.out.println("STARTING RECORDING STATE : "+recordingState);
                                System.out.println("SEND : "+Arrays.toString(record.getSend()));
                                System.out.println("RECV : "+Arrays.toString(record.getRecv()));
                                System.arraycopy(record.getRecv(),0,channelMatrix[record.getRecordingState()],0,recvArray.length);
                                forwardMarker=true;
                                break;
                            }
                            repeater+=INTERVAL;
                        }
                    }
                });
                chandyLamportInit.start();
            }
        }


        if(!coordinator){
            System.out.println("CP client talking to CP server on port : "+CP_PORT);
            System.out.println("NCP listener server running on port : "+NCP_PORT);
        }


        //Running the non coordinator part of the process
        //thread that enables procs communicate with CP and receive PIDs and neighbour host names etc from CP.
        Thread initializingThread = new Thread(() -> {
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            //register with coordinator
            try {
                BufferedReader reader = new BufferedReader(new FileReader(CONFIG));
                //read the CP hostname from dsConfig
                String[] params = reader.readLine().split(" ");
                Socket client = new Socket(params[1], CP_PORT);
                PrintWriter out = new PrintWriter(client.getOutputStream(), true);
                BufferedReader in = new BufferedReader(new InputStreamReader(client.getInputStream()));
                //lamport logical clock
                synchronized (llc_lock){
                    llc_value++;
                    //send register msg to CP
                    out.println(llc_value+",register");
                    System.out.println();
                    System.out.printf("<%d> registration initiated%n",llc_value);
                    System.out.println();
                }

                String[] totalNumberOfProcs = reader.readLine().split(" ");
                int arraySize = Integer.parseInt(totalNumberOfProcs[3]);
                //initialize holder arrays that hold the number of msgs in the respective category
                sendArray = new int[arraySize];
                recvArray = new int[arraySize];
                chnlArray = new int[arraySize];

                String line;
                while ((line = in.readLine()) != null) {
                    String[] parsedReceivedLine = line.split(",");

                    //if received msg is "registered" from CP
                    if (parsedReceivedLine[1].equals("registered")) {
                        System.out.println();
                        System.out.println("<i> registration success");
                        System.out.println();
                        //lamport logical clock
                        synchronized (llc_lock){
                            int senderProcTS = Integer.parseInt(parsedReceivedLine[0]);
                            int maxOfTS = Math.max(senderProcTS,llc_value);
                            llc_value=maxOfTS+1;
                            System.out.printf("<%d> received PID and neighbour list from coordinator%n",llc_value);
                            PROCESSID = Integer.parseInt(parsedReceivedLine[2]);
                            System.out.printf("    PID : %d%n",PROCESSID);
                            System.out.printf("    Neighbours----------PID%n");
                            for (int i = 3; i < parsedReceivedLine.length; i++) {
                                String[] detailsOfNeighbour = parsedReceivedLine[i].split(":");
                                localNeighbourSet.add(new Neighbour(detailsOfNeighbour[0],Integer.parseInt(detailsOfNeighbour[1])));
                                numberOfNeighbours++;
                                System.out.println("    "+detailsOfNeighbour[0]+"   "+detailsOfNeighbour[1]);
                            }
                            System.out.println("    -----------------------");
                            System.out.println();
                        }

                        //create local state file to write the snapshot results and a buffered writer to append to it
                        File file = new File("localstate_"+PROCESSID);
                        if(file.exists())
                            file.delete();
                        file.createNewFile();
                        stateWriter = new BufferedWriter(new FileWriter(file.getName(),true));


                        //post registration, initiate hello messages to neighbours
                        synchronized (lock) {
                            canSendHello = true;
                        }
                    }
                    while (true) {
                        if (!blockingQueue.isEmpty())
                            break;
                    }
                    if (canSendReady) {
                        //lamport logical clock
                        synchronized (llc_lock){
                            llc_value++;
                            System.out.println();
                            System.out.printf("<%d> sending ready to coordinator%n",llc_value);
                            System.out.println();
                            //send ready to CP
                            out.println(llc_value+",ready,"+PROCESSID);
                        }
                        //reset this value so that CP processes only one ready message
                        canSendReady = false;
                    }

                    //if received message is "compute" from CP
                    if (parsedReceivedLine[1].equals("compute")) {
                        synchronized (lock) {
                            //lamport logical clock
                            synchronized (llc_lock){
                                int senderProcTS = Integer.parseInt(parsedReceivedLine[0]);
                                int maxOfTS = Math.max(senderProcTS,llc_value);
                                llc_value=maxOfTS+1;
                                System.out.println();
                                System.out.printf("<%d> received compute from coordinator(PID#%s)%n",llc_value,parsedReceivedLine[2]);
                                System.out.println();
                            }
                            //this proc can send computes now to its neighbours
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
                //waiting for CP to signal this proc to send hello to its neighbours
                if (canSendHello)
                    break;
                try {
                    Thread.sleep(900);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }

            //for each neighbour, create new thread and establish new socket to send hello and compute msgs
            for (Neighbour n : localNeighbourSet) {
                Thread clientThreadAncillary = new Thread(() -> {
                    try {
                        Socket client = new Socket(n.getHostname(), NCP_PORT);
                        PrintWriter out = new PrintWriter(client.getOutputStream(), true);
                        //lamport logical clock
                        synchronized (llc_lock){
                            llc_value++;
                            sendArray[n.getId()-1]++;
                            //send hello to each neighbour
                            out.println(llc_value+",hello,"+PROCESSID);
                            System.out.printf("<%d> sending hello to PID %d%n",llc_value,n.getId());
                        }

                        //thread responsible for forwarding markers
                        Thread forwardMarkerThread = new Thread(()->{
                            while (true){
                                if(forwardMarker){
                                    //send marker message to each neighbour
                                    out.println(llc_value+",marker,"+PROCESSID+","+recordingState);
                                    System.out.println("FORWARDED MARKER TO PID# "+n.getId());
                                    synchronized (yetAnotherMarkerLock){
                                        resetMarkerCounter++;
                                    }
                                    while (true){
                                        if(resetMarkerCounter%numberOfNeighbours==0){
                                            //reset forward marker switch so that next recording state can be initiated
                                            forwardMarker = false;
                                            break;
                                        }
                                    }
                                }
                            }
                        });
                        forwardMarkerThread.start();


                        while (true) {
                            if (canSendCompute) {
                                //random sleep time
                                Thread.sleep(random.nextInt(100));
                                //lamport logical clock
                                synchronized (llc_lock){
                                    //randomly choose number of messages to be sent to each client
                                    //this includes procs to which no messages are send
                                    int numberOfMessages = random.nextInt(3);
                                    while (numberOfMessages!=0){
                                        llc_value++;
                                        sendArray[n.getId()-1]++;
                                        out.println(llc_value+",compute," + PROCESSID);
                                        numberOfMessages--;
                                    }
                                }
                            }
                            //close all message exchange on this connection
                            if(closeAllComputes)
                                break;
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


        //start main server thread to receive connections
        Thread serverThreadMain = new Thread(() -> {
            try {
                ServerSocket serverSocket = new ServerSocket(NCP_PORT);
                while (true) {
                    Socket client = serverSocket.accept();
                    Thread serverThreadAncillary = new Thread(() -> {
                        try {
                            PrintWriter out = new PrintWriter(client.getOutputStream(), true);
                            BufferedReader in = new BufferedReader(new InputStreamReader(client.getInputStream()));
                            String line;
                            while ((line = in.readLine()) != null) {
                                String[] parsedLine = line.split(",");
                                //if received message is hello
                                if (parsedLine[1].equalsIgnoreCase("hello")) {
                                    synchronized (llc_lock){
                                        int senderProcTS = Integer.parseInt(parsedLine[0]);
                                        int maxOfTS = Math.max(senderProcTS,llc_value);
                                        llc_value=maxOfTS+1;
                                        recvArray[Integer.parseInt(parsedLine[2])-1]++;
                                        System.out.printf("<%d> received hello from PID %d%n",llc_value,Integer.parseInt(parsedLine[2]));
                                    }
                                    synchronized (lock) {
                                        neighbourCounter++;
                                    }
                                    if (neighbourCounter == numberOfNeighbours)
                                        blockingQueue.add(1);
                                }

                                //if received message is compute
                                if(parsedLine[1].equalsIgnoreCase("compute")) {
                                    synchronized (llc_lock){
                                        int senderProcTS = Integer.parseInt(parsedLine[0]);
                                        int maxOfTS = Math.max(senderProcTS,llc_value);
                                        llc_value=maxOfTS+1;
                                        recvArray[Integer.parseInt(parsedLine[2])-1]++;
                                        if(!closeAllComputes)
                                            System.out.printf("<%d> received compute from PID %d%n",llc_value,Integer.parseInt(parsedLine[2]));
                                    }
                                }

                                //if received message is a marker
                                if(parsedLine[1].equalsIgnoreCase("marker")) {
                                    Thread interceptMarkers = new Thread(()->{
                                        try {
                                            Thread.sleep(1000);
                                            //marker sequence is basically recording state
                                            //buffer any new incoming recording state marker
                                            //process the marker message at the tip of the queue first
                                            Integer markerSequence = Integer.parseInt(parsedLine[3]);
                                            if(!mrkQueue.contains(markerSequence)){
                                                mrkQueue.put(markerSequence);
                                                pidQueue.put(Integer.parseInt(parsedLine[2]));
                                                System.out.println("BUFFER SIZE " + mrkQueue.size()+"********************"+"BUFFER FRONT ELEMENT "+mrkQueue.peek()+"********************");
                                            }
                                        } catch (InterruptedException e) {
                                            e.printStackTrace();
                                        }

                                        if(mrkQueue.peek()!=null){
                                            int markerMessage = mrkQueue.peek();
                                            //markers are processed differently based on whether
                                            //the proc is CP or NCP


                                            //if the marker receiving proc is a NCP
                                            if(!coordinator){
                                                synchronized (markerLock){
                                                    System.out.println("received MARKER# "+parsedLine[3]+" from PID# "+parsedLine[2]);
                                                    if(Integer.parseInt(parsedLine[3])==markerMessage){
                                                        //if this is the first time NCP is receiving a marker message belonging to a particular recording state
                                                        if(!firstMarkerReceived){
                                                            firstMarkerReceived = true;
                                                            System.out.println("SEND : "+Arrays.toString(sendArray));
                                                            System.out.println("RECV : "+Arrays.toString(recvArray));
                                                            System.arraycopy(recvArray,0,chnlArray,0,recvArray.length);
                                                            chnlArray[pidQueue.peek()-1] = 0;
                                                            //recording state increases by 1
                                                            recordingState = mrkQueue.peek();
                                                            System.out.println("STARTING RECORDING STATE : "+recordingState);
                                                            try {
                                                                if(recordingState==0){
                                                                    stateWriter.write("Recording count FINAL");
                                                                    stateWriter.newLine();
                                                                } else {
                                                                    stateWriter.write("Recording count "+recordingState);
                                                                    stateWriter.newLine();
                                                                }
                                                                stateWriter.write("SENT "+Arrays.toString(sendArray).replace("[","").replace("]","").replace(",",""));
                                                                stateWriter.newLine();
                                                                stateWriter.write("RECV "+Arrays.toString(recvArray).replace("[","").replace("]","").replace(",",""));
                                                                stateWriter.newLine();
                                                                stateWriter.flush();
                                                            } catch (IOException e) {
                                                                e.printStackTrace();
                                                            }
                                                            forwardMarker = true;
                                                        } else {
                                                            //if this is the NOT the first time NCP is receiving a marker message belonging to a particular recording state
                                                            neighbourMarkerCounter++;
                                                            chnlArray[Integer.parseInt(parsedLine[2])-1]
                                                                    = recvArray[Integer.parseInt(parsedLine[2])-1] - chnlArray[Integer.parseInt(parsedLine[2])-1];
                                                            if(neighbourMarkerCounter==numberOfNeighbours-1){
                                                                System.out.println("FINISHED RECORDING STATE : "+recordingState);
                                                                System.out.println("CHNL : "+Arrays.toString(chnlArray));
                                                                try {
                                                                    stateWriter.write("CHANNEL "+Arrays.toString(chnlArray).replace("[","").replace("]","").replace(",",""));
                                                                    stateWriter.newLine();
                                                                    stateWriter.newLine();
                                                                    stateWriter.flush();
                                                                } catch (IOException e) {
                                                                    e.printStackTrace();
                                                                }
                                                                //reset all counters to process the next marker of a different recording state
                                                                neighbourMarkerCounter = 0;
                                                                firstMarkerReceived = false;
                                                                mrkQueue.remove();
                                                                pidQueue.remove();
                                                                if(Integer.parseInt(parsedLine[3])==FINAL_MARKER){
                                                                    System.out.println("********************************************PROCESS TERMINATED (Ctrl + C to exit)");
                                                                    canSendCompute = false;
                                                                    closeAllComputes = true;
                                                                }
                                                                //check if theres another marker next in queue
                                                                if(mrkQueue.peek()!=null){
                                                                    firstMarkerReceived = true;
                                                                    System.out.println("SEND : "+Arrays.toString(sendArray));
                                                                    System.out.println("RECV : "+Arrays.toString(recvArray));
                                                                    System.arraycopy(recvArray,0,chnlArray,0,recvArray.length);
                                                                    chnlArray[pidQueue.peek()-1] = 0;
                                                                    recordingState = mrkQueue.peek();
                                                                    System.out.println("STARTING RECORDING STATE : "+recordingState);
                                                                    try {
                                                                        if(recordingState==0){
                                                                            stateWriter.write("Recording count FINAL");
                                                                            stateWriter.newLine();
                                                                        } else {
                                                                            stateWriter.write("Recording count "+recordingState);
                                                                            stateWriter.newLine();
                                                                        }
                                                                        stateWriter.write("SENT "+Arrays.toString(sendArray).replace("[","").replace("]","").replace(",",""));
                                                                        stateWriter.newLine();
                                                                        stateWriter.write("RECV "+Arrays.toString(recvArray).replace("[","").replace("]","").replace(",",""));
                                                                        stateWriter.newLine();
                                                                        stateWriter.flush();
                                                                    } catch (IOException e) {
                                                                        e.printStackTrace();
                                                                    }
                                                                    forwardMarker = true;
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            }

                                            //if proc is CP
                                            if(coordinator){
                                                synchronized (markerLock){
                                                    System.out.println("recieved MARKER# "+parsedLine[3]+" from PID# "+parsedLine[2]);
                                                    if(Integer.parseInt(parsedLine[3])==markerMessage){
                                                        neighbourMarkerCounter++;
                                                        channelMatrix[Integer.parseInt(parsedLine[3])]
                                                                [Integer.parseInt(parsedLine[2])-1]
                                                                = recvArray[Integer.parseInt(parsedLine[2])-1]
                                                                - channelMatrix[Integer.parseInt(parsedLine[3])]
                                                                [Integer.parseInt(parsedLine[2])-1];
                                                        if(neighbourMarkerCounter==numberOfNeighbours){
                                                            System.out.println("FINISHED RECORDING STATE : "+Integer.parseInt(parsedLine[3]));
                                                            System.out.println("CHNL : "+Arrays.toString(channelMatrix[Integer.parseInt(parsedLine[3])]));
                                                            try {
                                                                if(Integer.parseInt(parsedLine[3])==0){
                                                                    stateWriter.write("Recording count FINAL");
                                                                    stateWriter.newLine();
                                                                } else {
                                                                    stateWriter.write("Recording count "+parsedLine[3]);
                                                                    stateWriter.newLine();
                                                                }
                                                                stateWriter.write("SENT "+Arrays.toString(recordsBook[Integer.parseInt(parsedLine[3])].getSend()).replace("[","").replace("]","").replace(",",""));
                                                                stateWriter.newLine();
                                                                stateWriter.write("RECV "+Arrays.toString(recordsBook[Integer.parseInt(parsedLine[3])].getRecv()).replace("[","").replace("]","").replace(",",""));
                                                                stateWriter.newLine();
                                                                stateWriter.write("CHANNEL "+Arrays.toString(channelMatrix[Integer.parseInt(parsedLine[3])]).replace("[","").replace("]","").replace(",",""));
                                                                stateWriter.newLine();
                                                                stateWriter.newLine();
                                                                stateWriter.flush();
                                                            } catch (IOException e) {
                                                                e.printStackTrace();
                                                            }
                                                            //reset all
                                                            neighbourMarkerCounter = 0;
                                                            mrkQueue.remove();
                                                            if(Integer.parseInt(parsedLine[3])==FINAL_MARKER){
                                                                System.out.println("********************************************PROCESS TERMINATED (Ctrl + C to exit)");
                                                                canSendCompute = false;
                                                                closeAllComputes = true;
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    });
                                    interceptMarkers.start();
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


    //function called at the begining to parse the dsConfig file
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
                if (parsedLines[0].equalsIgnoreCase("INTERVAL")){
                    INTERVAL = Integer.parseInt(parsedLines[1]);
                }
                if (parsedLines[0].equalsIgnoreCase("TERMINATE")){
                    TERMINATE = Integer.parseInt(parsedLines[1]);
                }
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

//hold each recording state in memory
//used exclusively by the CP
class Record {

    boolean done = false;
    int recordingState;
    int[] send;
    int[] recv;
    int[] chnl;

    public Record(int recordingState, int[] send, int[] recv) {
        this.recordingState = recordingState;
        this.send = new int[send.length];
        System.arraycopy(send,0,this.send,0,send.length);
        this.recv = new int[recv.length];
        System.arraycopy(recv,0,this.recv,0,recv.length);
    }

    public boolean isDone() {
        return done;
    }

    public void setDone(boolean done) {
        this.done = done;
    }

    public int getRecordingState() {
        return recordingState;
    }

    public void setRecordingState(int recordingState) {
        this.recordingState = recordingState;
    }

    public int[] getSend() {
        return send;
    }

    public void setSend(int[] send) {
        this.send = send;
    }

    public int[] getRecv() {
        return recv;
    }

    public void setRecv(int[] recv) {
        this.recv = recv;
    }

    public int[] getChnl() {
        return chnl;
    }

    public void setChnl(int[] chnl) {
        this.chnl = chnl;
    }
}
