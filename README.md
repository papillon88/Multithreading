# Multithreading

This repo consists few of my multhreaded project pursuits. 

**Amongst the noticable are :**

## A robust distributed system with Lamport clock and Chandy-Lamport snapshotting
- The [program](https://github.com/papillon88/Multithreading/blob/a70761c1572b6ec7fa6135a2908a3bdaac25d7c4/src/dproc.java) bootstraps a distributed system by reading a network configuration file [dsConfig](https://github.com/papillon88/Multithreading/blob/a70761c1572b6ec7fa6135a2908a3bdaac25d7c4/src/dsConfig). After the bootstrap, the coordinator initiates global snapshots based on [Chandy-Lamport](https://en.wikipedia.org/wiki/Chandy-Lamport_algorithm) algo at INTERVAL values till TERMINATION value. All local state infos are recorded in a file named "localstate_x" where x is the PID assigned to nodes by the coordinator. The program ends gracefully with properly recorded local and channel states. **The program works best when the INTERVAL values are in 100s and TERMINATION value is in 1000s.**
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
  
## Performance evaluation of Suzuki-Kasami Critical Section access algorithm in a distributed system
   - The [program](https://github.com/papillon88/Multithreading/blob/0321cfb539a440a45536197322d291818381f423/src/deploy.java) bootstraps a distributed system using the dsConfig file mentioned above. It then implements the [Suzuki-Kasami](https://en.wikipedia.org/wiki/Suzuki%E2%80%93Kasami_algorithm) algorithm and finally initiates a performance evaluation w.r.t wait time, synchronization delay and average number of messages required for critical section access in the distributed system. The program does this for n = 5,10,15,20 and 25 processes. For each n, there is a separate dsConfig file as the network topology is that of a complete connected graph. The program ends with giving process wise and overall average of each of the performance index.
   - Steps to run the program :
       - The file is [deploy.java](https://github.com/papillon88/Multithreading/blob/0321cfb539a440a45536197322d291818381f423/src/deploy.java).
       - Rest of the process is same as described in Chandy Lamport algorithm above. 
       
## Raymond tree mutual exclusion performance evaluation in a distributed ecosystem
   - The [program](https://github.com/papillon88/Multithreading/blob/f660e207a76319cc4cab21d1f7fb993351f72537/src/deploy.java) bootstraps a distributed system using the dsConfig file mentioned above. It then implements the [Raymond tree mutual exclusion solution](https://en.wikipedia.org/wiki/Raymond%27s_algorithm) algorithm and finally initiates a performance evaluation w.r.t wait time, synchronization delay and average number of messages required for critical section access in the distributed system. The program does this for n = 5,10,15,20 and 25 processes. For each n, there is a separate dsConfig file as the network topology is that of a complete connected graph. The program ends with giving process wise and overall average of each of the performance index.
   - Steps to run the program :
       - The file is [deploy.java](https://github.com/papillon88/Multithreading/blob/f660e207a76319cc4cab21d1f7fb993351f72537/src/deploy.java).
       - Rest of the process is same as described in Chandy Lamport algorithm above. 
    
