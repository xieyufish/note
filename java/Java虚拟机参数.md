## Java虚拟机参数

- -Xms和-Xmx：分配指定虚拟机中堆内存的大小；
- -Xss和-Xsx：分配指定栈内存的大小；每个线程的栈还是总的栈还待确认理解；
- -XX:+HeapDumpOnOutOfMemoryError：让虚拟机在内存出现异常时Dump出当前的内存**堆**转储快照以便事后进行分析；
- -XX:+PrintGC
- -XX:+PrintGCDetails
- -XX:+PrintGCTimeStamps
- -XX:+PrintGCDateStamps
- -XX:+PrintHeapAtGC
- -Xloggc​:path