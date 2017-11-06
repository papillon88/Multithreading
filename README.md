# Multithreading

This repo consists few of my multhreaded project pursuits. 

**Amongst the noticable are :**

- **Distributed system with Chandy-Lamport snapshotting**
  - The [program](https://github.com/papillon88/Multithreading/blob/a70761c1572b6ec7fa6135a2908a3bdaac25d7c4/src/dproc.java) bootstraps a distributed system by reading a network configuration file [dsConfig](https://github.com/papillon88/Multithreading/blob/a70761c1572b6ec7fa6135a2908a3bdaac25d7c4/src/dsConfig). After the bootstrap, the coordinator initiates global snapshots based on Chandy-Lamport algo at <INTERVAL> values till <TERMINATION> value. All local state infos are recorded in a file named "localstate_x" where x is the PID assigned to nodes by the coordinator. The program ends gracefully with properly recorded local and channel states.  
    The dsConfig consists of the following parameters :
    - COORDINATOR net01.utdallas.edu --> hostname of the machine that acts as the coordinator in the DS
    - NUMBER OF PROCESSES 5 --> total number of processes in the system
    - INTERVAL 100 --> This is the lamport logical clock value that defines the period over which snapshotting happens.
    - TERMINATE 5000 --> This is the lamport logical clock value that defines the termination.
    - NEIGHBOR LIST  ---> defines the network topology
        2 1 5 4  
        3 1 4  
        1 2 5 3  
        5 4 2 1  
        4 2 3 5  
  - Steps to run the program :
    - The university network consists 45 systems. Any VPN with a naming server can also be used for this purpose. 
    - FTP [dproc.java](https://github.com/papillon88/Multithreading/blob/a70761c1572b6ec7fa6135a2908a3bdaac25d7c4/src/dproc.java) and dsConfig file to any machine. Because this is a DS, transferring the files to one machine makes it available in all the machines. dsConfig should be present in the same directory as the drpoc.java file. 
    - Compile the drpoc.java
    - Choose any machine to be the coordinator. Run the compiled file on that machine using "java drpoc -c" command.
    - On the other machines, run the file as "java drpoc" only. 
  
    
