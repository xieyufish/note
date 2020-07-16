## Spark Standalone模式的高可用环境配置

​	当Spark以Standalone模式运行时，由于Master/Slaves的特性，会存在单点故障的问题。Spark可以通过两种方式来解决这个问题，分别是基于文件系统的单点恢复和基于zookeeper的StandBy Master。因为在生产环境不会选择基于文件系统的单点恢复技术，所以这篇文章几种讲解基于zookeeper的单点故障恢复技术。

### 1. Zookeeper环境的安装配置

​	这一部分可以参考我的文章：Zookeeper的安装配置，在这里不再赘述。

### 2. Spark高可用的配置

​	Spark的高可用配置很简单，基本过程就是修改spark的配置文件，让spark支持zookeeper选举Master节点，接着就是将配置文件在集群中同步，并在集群中启动多个Master（**这是关键步骤，我就是根据某个网友的文章配置高可用，由于他没有提供这关键的一个步骤，让我耗费了几个小时的时间**）。

#### 2.1 修改Spark的spark-env.sh配置文件

![spark_35](images\spark_35.png)

| 选项                         | 说明                                       |
| -------------------------- | ---------------------------------------- |
| spark.deploy.recoveryMode  | 指定故障恢复的技术, 可选FILESYSTEM,ZOOKEEPER和NONE,默认NONE |
| spark.deploy.zookeeper.url | 指定zookeeper运行的节点,一般就是备用master节点上的zookeeper访问url |
| spark.deploy.zookeeper.dir | 指定挂载zookeeper树上的根节点名字, 默认也是/spark        |

#### 2.2 将修改后的spark-env.sh文件拷贝到集群中的其他节点上

​	可通过scp命令，或者其他集群管理运维工具均可。

### 3. 测试

​	**首先启动zookeeper集群**

​	这里不再赘述，可查看上文说的文章

​	**启动Spark集群**

​	通过命令**sbin/start-all.sh**启动spark集群

![spark_36](images\spark_36.png)

 ![spark_37](images\spark_37.png)

我们要注意Spark集群状态的变化，最先是由standby状态（这个状态耗时较少，没截到图），然后变为recovering状态（由之前的状态在恢复，之前节点是为UNKNOWN），最终变为alive状态（相应的其他节点变为DEAD）。

此时，如果我们不在其他节点上启动备用的master进程，直接通过命令**sbin/stop-master.sh**杀死master主机上的master进程，我们是不会看到任何其他的节点会启动master进程的（**我就是在这一点上耗费了大把时间**）

此时，我们的spark集群已经正常运行起来。



好，我们接下来在**node1**节点上运行命令**sbin/start-master.sh**命令来启动备用的master进程 

![spark_38](images\spark_38.png)

我们可以看到，node1节点上的master进程成功启动，此时spark集群中存在两个master进程，那么我们通过浏览器访问**master:8080**看到的情况是什么样的呢？

![spark_39](images\spark_39.png)

我们可以看到，master主机上的master管理的节点信息并没有发生任何的变化。

同时，我们也可以通过**node1:8080**来查看另一个master进程的情况：

![spark_40](images\spark_40.png)

我们可以看到，node1上的master进程并没有任何的worker节点的信息，并且此时node1的状态是**standby**的。

接下来，我将master主机上的master进程杀死，在master主机上执行命令**sbin/stop-master.sh**:

 ![spark_41](images\spark_41.png)

 ![spark_42](images\spark_42.png)

此时，node1的状态已经变为alive，并且他开始接管了三个worker节点。

至此，spark的高可用集群环境配置结束。

### 4. 问题

​	正如我上文提到过的，遇到的一个主要的问题就是没有再多个节点上启动master进程，这一点一定要记住。还有一点就是集群中每个机器一定要是可以通过ssh免密登录的，原先我以为只要保持master主机和其他node节点机器可以免密登录即可，各个node节点之间没必要ssh免密登录，现在看来这样时不行的，因为一点master主机节点宕机或者出现故障，就可能有其他的node节点来接替master这个角色，这个时候需要可以和其他节点之间互相通信。