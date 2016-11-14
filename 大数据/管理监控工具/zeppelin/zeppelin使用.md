## 可交互数据分析工具-Zeppelin

​	**Zeppelin**是一个基于web的交互式数据分析平台。通过Zeppelin，我们可以使用SQL、Scala等语言来创建漂亮的基于数据驱动的交互式协作文档。Zeppelin提供给我们的功能有：数据摄入、数据发现、数据分析以及数据的可视化展示和在多个用户之间共同协作。

​	同时，**Zeppelin**是基于可插拔式的方式工作，支持多种后端数据结构和语言，比如：Spark，cassandra，R语言，Hive，Tajo，Hbase，python等等。默认情况下，Zeppelin提供了针对Spark的内置支持。下面我们将详细讲解Zeppelin与Spark是如何协同工作的。

### 1. Zeppelin下载和安装

#### 1.1 下载

​	我们可以从[Zeppelin官网](http://zeppelin.apache.org/)找到Zeppelin的下载入口，如下：![1](images\1.png)

点击下拉菜单的选项进入下载页面：![2](images\2.png)

我们可以看到，Zeppelin已经预先给我们编译好了两个类型的tgz包，那么我们应该选择哪个包进行下载呢？这个主要看自己的需求是什么。如果我们只需要**Spark**有关的解释器，那么我们可以选择第二个，如果你想要Zeppelin完整的所有的解释器那你可以选择第一个，我这里因为事先没了解，所以我下载的是第一个tgz包。但是，当你点击这个链接的时候你会发现没有任何反应，也就是说根本下载不了，那么你可以点击[这里](http://www.apache.org/dyn/closer.cgi/zeppelin)进行下载。我写这篇文章时，Zeppelin的最新版本是0.6.2，所以我下载的是这个最新版。

#### 1.2 安装

​	下载好Zeppelin的压缩包之后，解压，解压之后就相当于Zeppelin的基本安装已经完成。我们可以通过如下命令启动Zeppelin服务。
​	**bin/zeppelin-daemon.sh start**
启动成功之后，将会产生一个**ZeppelinServer**进程，我们可以通过jps命令来查看。 ![3](images\3.png)

同时，我们也可以通过浏览器来查看，在浏览器中通过http://IP:8080来访问，Zeppelin默认绑定的http访问端口为8080![4](images\4.png)

至此，Zeppelin的基本安装已经宣告结束。

可以通过如下命令停止Zeppelin：
​	**bin/zeppelin-daemaon.sh stop**

**注意**
​	Zeppelin也是基于java环境的，所以在我们系统的环境变量中也要配置好java环境，如果在系统环境变量中没有配置java环境，也可以通过Zeppelin的配置文件**conf/zeppelin-env.sh**来执行java的位置。

### 2. 认识和使用Zeppelin

​	Zeppelin的认识和使用非常的简单，而且他的官方文档介绍的也很详细，在此我不再赘述，请大家起步[官方文档](http://zeppelin.apache.org/docs/0.6.2/)查看

### 3. 集成Spark

​	正如我们前面介绍的，Zeppelin是一个交互式的数据分析平台，那就意味着Zeppelin是专门处理数据相关的事情的，而Spark在当下是非常流行的大数据处理框架，所以Zeppelin内置了针对Spark的支持。下面我将详细介绍Zeppelin如何配置Spark，并通过一个简单的demo来展示Zeppelin在数据分析方面的**便捷性**和**交互性**。

#### 3.1 Spark配置

​	Zeppelin集成Spark的前提是我们已经拥有了一个Spark集群，至于Spark的安装配置并不是本文的重点，因此本文不会介绍Spark的安装和配置，可以查看我的相关文章来学习Spark的安装配置。

- 进入zeppelin的配置目录：conf
- 分别拷贝zeppelin-env.sh.template和zeppelin-site.xml.template文件，并分别重命名为zeppelin-env.sh和zeppelin-site.xml
- 添加或者修改zeppelin-env.sh配置文件中的**SPARK_HOME**变量，修改这个变量的值指向你的spark的home目录，并保存
- 因为Spark启动之后，默认的web访问端口也是8080，这会造成冲突，所以这里我们修改Zeppelin的默认web端口为8888，一样是修改zeppelin-env.sh配置文件，添加export ZEPPELIN_PORT=8888并保存。

启动Spark，在启动Zeppelin，接下来将通过一个demo来验证我们的Spark配置是否成功。

#### 3.2 Spark demo

​	在Zeppelin上要运行数据分析代码，都是通过note这样一个单元来执行的，在创建spark demo之前，我们新建一个note，创建方式如下：![5](images\5.png)



![6](images\6.png)

 ![7](images\7.png)

现在，我们用spark读取一个本地文件，并将这个文件保存为spark中一个表，接着针对这个表做sql查询操作。步骤如下：

![9](images\9.png)				在代码中的sc变量即为：SparkContext的一个实例，这是由Zeppelin提供的内置实例，跟spark-shell中的sc变量一样，Zeppelin提供的其他变量有：SQLContext的实例sqlContext，ZeppelinContext的实例z。

​	在上述代码运行成功之后，也就是在Spark中成功的创建了表：bank，接下来我们可以针对bank表做一些数据分析的操作，如下：![10](images\10.png)

![11](images\11.png)

#### 3.3 问题

​	在运行demo时遇到了一个问题，当编写好scala代码执行时，报了一个错误，说是jackson的版本问题，问题截图如下： ![8](images\8.png)

这是因为Zeppelin所使用的jackson jar为2.5.3，跟spark所使用的jar包不兼容匹配造成的，这个问题可以通过将spark的jars目录下对应的jackson的jar包拷贝到zeppelin的lib目录下，并删除zeppelin的lib中原有得jackson jar包即可。

**总结**
​	zeppelin的使用可以大大的简化我们执行spark程序的工作，同时多样化的结果展示也为我们提供了很大的方便，zeppelin还有其他很多好的功能以及其他的解释器的支持，这在以后的工作中在慢慢探索吧。

