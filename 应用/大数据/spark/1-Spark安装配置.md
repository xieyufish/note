## Spark安装配置

[TOC]

### 下载

​	进入[Apache Spark官网](http://spark.apache.org/)的[下载页](http://spark.apache.org/downloads.html)，截止我写这篇文章的日期，spark的最新版本为2.0.0，因为我的**hadoop版本是2.6.4**的，所以我选择spark的版本是2.0.0，Hadoop是2.6。

![spark_5](images\spark_5.png)

### 环境配置

集群环境：

|  主机名   |       IP        |        hadoop环境         |     Scala环境      | Java环境                 |
| :----: | :-------------: | :---------------------: | :--------------: | ---------------------- |
| master | 192.168.146.146 | /usr/local/hadoop-2.6.4 | /usr/local/scala | /usr/local/jdk1.7.0_79 |
| node1  | 192.168.146.145 | /usr/local/hadoop-2.6.4 | /usr/local/scala | /usr/local/jdk1.7.0_79 |
| node2  | 192.168.146.144 | /usr/local/hadoop-2.6.4 | /usr/local/scala | /usr/local/jdk1.7.0_79 |
| node3  | 192.168.146.143 | /usr/local/hadoop-2.6.4 | /usr/local/scala | /usr/local/jdk1.7.0_79 |

​	将下载好的**spark-2.0.0-bin-hadoop2.6.tgz**拷贝到主机master上的/usr/local目录中，并执行命令解压到当前目录中：**tar -zxf spark-2.0.0-bin-hadoop2.6.tgz **，解压后的目录结构如下：

​                                     ![ ](images\spark_6.png)

​	修改目录conf中的配置文件来配置spark的运行环境，conf目录中包含的文件有：

​                               ![spark_7](images\spark_7.png)

​	我们主要修改**spark-env.sh**这个文件。拷贝spark-env.sh.template并重命名为spark-env.sh：
​            **cp spark-env.sh.template spark-env.sh**，结果如下：

​                                ![spark_8](images\spark_8.png)

​	接下来修改spark-env.sh，主要添加以下几项配置：

|        配置项        |                 值                  |        说明         |
| :---------------: | :--------------------------------: | :---------------: |
|     JAVA_HOME     |       /usr/local/jdk1.7.0_79       |    指向jdk的安装路径     |
|    HADOOP_HOME    |      /usr/local/hadoop-2.6.4       |   指向hadoop的安装路径   |
|    SCALA_HOME     |          /usr/local/scala          |     scala的安装      |
|  HADOOP_CONF_DIR  | /usr/local/hadoop-2.6.4/etc/hadoop |  hadoop配置文件所在的目录  |
| SPARK_MASTER_HOST |          192.168.146.146           | spark集群master运行主机 |

 ![spark_9](images\spark_9.png)

​	执行sbin/start-all.sh命令，查看是否能够启动spark，查看是否有Worker和Master进程。

​                   ![spark_10](images\spark_10.png)

​	运行spark提供的示例检测：**bin/run-example JavaSparkPi  5  2**

![spark_11](images\spark_11.png)

![spark_12](images\spark_12.png)

​	至此，单个节点的Spark环境配置结束。

### 多个节点的集群环境配置

​	在上述单个节点启动成功的基础上，配置多个节点集群环境是比较简单的一件事情。

- 修改master上节点的slaves配置文件来配置Worker节点的位置，这里我将node1、node2、node3作为Worker节点的运行机器，在conf/slaves(复制slaves.template)中添加node1、node2和node3。 ![spark_13](images\spark_13.png)
- 将master上配置好的spark目录文件全部分别拷贝到node1、node2和node3所在机器上(可以通过**ansible**这个工具来操作)。
- 通过命令sbin/start-all.sh启动spark集群 ![spark_14](images\spark_14.png)

​     ![spark_15](images\spark_15.png)

- 也可以通过浏览器来查看集群状态，在浏览器中通过spark主节点的8080端口可以查看集群状态，在浏览器中输入：http://master:8080 ![spark_16](images\spark_16.png)

### 运行测试

​	**Standalone模式的测试**

​	在shell环境下运行Spark提供的案例程序JavaSparkPi，通过如下命令：
​	**bin/spark-submit --class org.apache.spark.examples.JavaSparkPi --deploy-mode cluster examples/jars/spark-examples_2.11-2.0.0.jar 10 4**

​	shell界面输出如下信息： ![spark_18](images\spark_18.png)

​	从shell界面我们不能得到什么信息，我们可以通过浏览器来查看执行这个应用的具体信息，在浏览器中输入http://master:8080，我们将看到如下信息：

![spark_19](images\spark_21.png)

​	点击**Completed Applications**中的链接，我们可以查看运行这个应用所消耗的资源情况： ![spark_20](images\spark_20.png)

​	点击Completed Drivers下的超链来查看应用程序driver进程所在节点的信息，通过这个节点我们也可以查看整个应用程序的输入结果信息。![spark_22](images\spark_22.png)

​	点击上图中的**stdout**，我们可以查看整个应该程序的输出结果。如下： ![spark_23](images\spark_23.png)

### 在yarn上的运行测试这里不再记录，请自行测试