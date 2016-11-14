## SparkStreaming编程指导

### 1. Overview

SparkStreaming模块是Spark核心的扩展，SparkStreaming的加入使得Spark的可扩展性，吞吐量，容错性以及针对实时流数据的处理能力都有很大的提升。使得Spark可以从**Kafka**、**Flume**、**Kinesis**甚至是**TCP socket**等数据源来获取数据，并针对这些数据进行复杂的算法处理获取我们想要的结果。这些数据的结果可以保存在分布式文件系统、数据库或者直接在线展示，在实际处理中，我们甚至可以使用机器学习和图像处理算法来处理我们获得的流数据。

![](images\spark_24.png)

​                                                                                     **流处理架构流程**

在Spark内部，SparkStreaming接收实时输入数据流，并将接收到的数据流分割为数据块，然后这些分割的数据块被Spark引擎处理产生最后的结果数据块。 

![spark_25](images\spark_25.png)

​                                                                               **数据流流向**

Spark提供一个叫做DStream(discretized stream中文名字：离散流)的抽象概念来表示接收的数据流。DSteam可以通过从**Kafka**、**Flume**、**Kinesis**和**TCP socket**获得的数据流来创建，也可以从已存在的DStream来构建。在Spark内部，一个DStream其实是一系列的RDD组成（下面有比较详细介绍）。

这篇文章主要介绍的是：我们通过Java怎么来编写Spark Streaming程序。

### 2. A Quick Example

在我们详细解说如何编写Spark Streaming程序之前，让我们先来看一个简单的例子。这个例子的功能是：计算从TCP socket接收的数据流中每个单词出现的次数。

```java
package com.shell.hadoop.spark.streaming;

import java.util.Arrays;
import java.util.Iterator;
import java.util.regex.Pattern;

import org.apache.spark.SparkConf;
import org.apache.spark.api.java.function.FlatMapFunction;
import org.apache.spark.api.java.function.Function2;
import org.apache.spark.api.java.function.PairFunction;
import org.apache.spark.storage.StorageLevel;
import org.apache.spark.streaming.Duration;
import org.apache.spark.streaming.api.java.JavaDStream;
import org.apache.spark.streaming.api.java.JavaPairDStream;
import org.apache.spark.streaming.api.java.JavaReceiverInputDStream;
import org.apache.spark.streaming.api.java.JavaStreamingContext;

import scala.Tuple2;

/**
* 运行方式：NetworkWordCount host port
* 示例：NetworkWordCount localhost 9999
**/
public class NetworkWordCount {
	private static final Pattern SPACE = Pattern.compile(" ");
	
	public static void main(String[] args) throws InterruptedException {
		if (args.length < 2) {
			System.err.println("Usage: NetworkWordCount <hostname> <port>");
			System.exit(1);
		}
		
		SparkConf sparkConf = new SparkConf().setAppName("NetworkWordCount");
        // 创建一个SparkStreaming对象,其中的时间new Duration()参数是一个时间间隔值
        // 根据这个间隔值内接收的数据来创建一个数据块或者一个RDD
		JavaStreamingContext ssc = new JavaStreamingContext(sparkConf, new Duration(1000));
		
        // 绑定数据流
		JavaReceiverInputDStream<String> lines = ssc.socketTextStream(args[0], Integer.parseInt(args[1]), StorageLevel.MEMORY_AND_DISK_SER());
		JavaDStream<String> words = lines.flatMap(new FlatMapFunction<String, String>() {

			@Override
			public Iterator<String> call(String t) throws Exception {
				return Arrays.asList(SPACE.split(t)).iterator();
			}
			
		});
		
		JavaPairDStream<String, Integer> wordCounts = words.mapToPair(new PairFunction<String, String, Integer>() {

			@Override
			public Tuple2<String, Integer> call(String t) throws Exception {
				return new Tuple2<>(t, 1);
			}
			
		}).reduceByKey(new Function2<Integer, Integer, Integer>() {

			@Override
			public Integer call(Integer v1, Integer v2) throws Exception {
				return v1 + v2;
			}
			
		});
		
		wordCounts.print();
		ssc.start();
		ssc.awaitTermination();
	}
}
```

- 首先，我们创建一个**JavaStreamingContext**的实例ssc，这是SparkStreaming编程中的主入口；
- 利用ssc实例，我们可以创建一个DStream，这个DStream表示的是接收localhost上9999端口TCP数据流；
- **lines**表示将要从本地9999端口接收的数据流，在这个数据流中的每一条记录是一行文本数据，然后我们将lines使用flatMap操作分割成字；
- **flatMap**是一个DStream操作，通过这个操作我们创建了一个新的DStream，lines经过flatMap操作之后将被分割为多个字，用**words** 来表示这个新生成的DStream；
- 接下来我们通过mapToPair和reduceByKey来计算每个字出现的次数。

需要注意的是，跟Spark中RDD计算一样，在上面这些步骤完成之后，程序并不会真正开始执行计算直到我们调用**scc.start()**方法。

### 3. 基本概念

#### 3.1 初始化StreamingContext

为了创建一个SparkStreaming程序，我们必须要创建一个JavaStreamingContext对象，JavaStreamingContext对象可以通过**SparkConf**来创建：

```java
SparkConf sparkConf = new SparkConf().setAppName("NetworkWordCount");
JavaStreamingContext ssc = new JavaStreamingContext(sparkConf, new Duration(1000));
```

也可以通过一个已经存在的JavaSparkContext对象来创建：

```java
JavaSparkContext sc = ...   //existing JavaSparkContext
JavaStreamingContext ssc = new JavaStreamingContext(sc, Durations.seconds(1));
```

在我们创建完JavaStreamingContext之后，我们必须还要做如下事情：

1. 创建DStream来定义一个输入数据流；
2. 通过运用transformation和output操作来定义流计算；
3. 通过使用JavaStreamingContext.start()方法启动数据接收和数据处理；
4. 使用JavaStreamingContext.awaitTermination()等待处理被停止；
5. 也可以通过JavaStreamingContext.stop()手动停止处理。

**关键点**

- 一旦JavaStreamingContext.start()开始执行了，就不能再增加新的计算，在代码中的表现就是在start()方法之后不能再有flatMap这样的函数调用。
- stop之后不能再start；
- 同一时刻一个jvm中只允许一个JavaStreamingContext存在；
- stop方法也会终止掉JavaSparkContext，如果我们只想终止JavaStreamingContext，那么可以调用stopSparkContext（false）；
- 一个JavaSparkContext可以被用来创建多个JavaStreamingContext，只要在创建新的JavaStreamingContext对象时之前创建的JavaStreamingContext已经停止了。

#### 3.2 DStream

DStream是SparkStreaming提供的一个核心概念。它表示的是一个连续的数据流，既可以是从数据源输入的数据流(称作:输入DStream)，也可以是基于输入DStream经过某些操作而新生成的。在Spark内部，DStream由一个连续的RDD序列组成，关于RDD的概念，请移步我的：Spark编程基本概念入门。在DStream中的每一个RDD包含的数据是在确定时间段内从数据源接收的数据，用图表示如下： 

![spark_26](images\spark_26.png)

任何基于DStream的操作都会在底层转换为针对RDD的操作。例如，在我们Quick example中，把lines转换为words时，flatMap操作会被应用到lines DStream中的每一个RDD上来生成最终的words DStream，用图描述如下：![spark_27](images\spark_27.png)

这些底层的RDD操作都由Spark引擎计算。DStream的操作隐藏了很多细节，而让开发者更加关注DStream。

#### 3.3 Input DStreams and Receivers

Input DStreams代表的是直接从数据流源接收数据的DStream。在我们的Quick example中，lines就是这样的一个Input DStream，它代表从一个netcat服务器接收数据(这里以netcat作为数据源为例)。每一个Input DStream（除了file stream）会和一个Receiver对象关联，Receiver对象负责从数据流源接收数据并存储在Spark的内存中供后续处理。

Spark提供了基于两类数据流源的处理：

- **Basic sources：**源可以直接通过JavaStreamingContext的api访问。比如：文件系统源和socket连接源。
- **Advanced sources：**必须通过额外的工具类才能访问，比如：Kafka、Flume和Kinesis。

稍后我们会讨论两种类型里面的一些源。

如果我们想在我们的应用中同时接受多个数据流源，那么我们可以创建多个Input DStream。这样也会创建多个Receiver对象来同时接受输入数据(会创建后台receiver线程接收数据)。要注意的是，因为Spark应用是一个长时间运行的任务，会占用分配给Spark程序的cpu内核，因此，我们要分配足够的cpu内核让Spark应用在接受数据的同时可以对这些数据进行处理。

**关键点**

- 当我们在本地运行Spark Streaming应用时，我们不能使用local或者local[1]这样的值作为master参数的url值。因为如果我们使用Input DStream时，如果只有单个线程，那么这个线程将被用于Receiver对象来接收数据，而没有多余的线程来处理我们的接收数据，所以以locally方式运行Spark Streaming应用的时候，启动的线程数必须要大于接收数据的Receiver对象。
- 将上述逻辑应用到集群模式下，就得要保证Spark Streaming应用分配到的cpu内核数要大于接收数据的Receiver对象。否则将没有多余的线程来处理接收到的数据。

#### 3.4 Basic Sources

在Quick example中的例子，我们看到可以通过ssc.socketTextStream()方法来接收TCP socket的数据。除了socket外，Spark Streaming还提供了以文件作为输入源的方法。

- **File Streams：**为了从兼容HDFS API的文件系统（HDFS，S3，NFS等）中读取数据，我们可以这样做

  ```java
  streamingContext.fileStream<KeyClass, ValueClass, InputFormatClass>(dataDirectory);
  ```

  Spark Streaming会监控dataDirectory目录并且处理在这个目录中的文件(子目录中的文件不会被处理)。注意：

  - 目录下的文件必须要有相同的数据格式；
  - 放置到目录中的文件必须是一次性命名或者已经是修改了名字的，也就是说文件放置在目录后，不能再修改文件名；
  - 文件被放置在目录中后，内容不能再修改，添加也不行，否则新增的内容将不会被处理。

  针对简单的文本文件，有更简单的方法：streamContext.textFileStream(dataDirectory)。file stream（文件输入流）不需要运行Receiver对象，所以没必要为Receiver分配cpu。

- **Streams based on Custom Receivers：**可以通过自定义Receiver来创建DStream。下面会有详细的讲解。

- **Queue of RDDs as a Stream：**为了使用测试数据测试SparkStreaming应用，我们可以基于RDD队列创建一个DStream，使用JavaStreamingContext.queueStream(queueOfRDDs)。每一个添加到队列中的RDD就跟从数据源接收到的时间段数据一样。

#### 3.5 Advanced Sources

这一类的数据流源必须要用到其他外部的非Spark工具包。具体的包信息如下表：不同版本可以通过maven查找。

| Source  | Artifact                         |
| ------- | -------------------------------- |
| Kafka   | spark-streaming-kafka-0-8_2.11   |
| Flume   | spark-streaming-flume_2.11       |
| Kinesis | spark-streaming-kinesis-asl_2.11 |

因为这些数据流源在Spark shell环境中不能够获取，所以基于这些源的应用不能在shell环境中做测试。

#### 3.6 Custom Sources

Input DStream也可以通过自定义源来创建，我们需要做得就是实现我们自己的Receiver对象来接收自定义数据源并把数据存到Spark上。这里就不讲了，具体怎么实现可以参考Spark发布包中的例子代码程序。

#### 3.7 Receiver Reliability

从数据输入流的可靠性判断，可以分为两种类型的数据输入流。像Kafka和Flume这一类型的数据输入流，当从这些流获取数据时，我们接收到数据之后可以发送确认接收响应，这样可以保证所有数据不会丢失。

- Reliable Receiver：会发送确认消息来确认数据接收成功；
- Unreliable Receiver：不会发送确认消息。

### 4. Transformations on DStreams

跟RDDs一样，也可以针对DStream执行Transformation操作。比如常见的map、flatMap、filter、reparation、union、count、reduce、countByValue等一系列相关的Transformation操作。下面挑几个Transformation操作来进行讨论。

#### 4.1 UpdateStateByKey操作

updateStateByKey操作随着新数据的接收可以更新之前保存的状态。使用这个特性必须要做两步操作。

1. 定义状态：状态值可以是任意类型

2. 定义状态更新函数：实现怎么根据新接收的数据和之前状态来更新状态

在接收到的每个时间片数据上，Spark都会使用这个状态更新函数到每个key上，不管时间片上的数据是否更新了，如果更新状态函数返回None则对应的key-value对将被淘汰（**这里没明白是什么意思**）。

例如我们想要根据接收数据源来维护接收单词的数量，我们可以这样做：

```java
// 更新状态函数实现,Optional分别为老状态和返回的新状态
Function2<List<Integer>, Optional<Integer>, Optional<Integer>> updateFunction =
  new Function2<List<Integer>, Optional<Integer>, Optional<Integer>>() {
    @Override 
    public Optional<Integer> call(List<Integer> values, Optional<Integer> state) {
      Integer newSum = ...  // add the new values with the previous running count to get the new count
      return Optional.of(newSum);
    }
  };
```

将这个函数运用到一个DStream对象上：

```java
JavaPairDStream<String, Integer> runningCounts = pairs.updateStateByKey(updateFunction);
```

updateFunction函数将被每个单词调用。这个特性使用的前提是必须要配置checkPoint操作目录。

#### 4.2 Transform操作

transform操作（它的变体transformWith操作跟他一样）允许任意的RDD-to-RDD函数被运用。什么意思呢？比如：我们想将一个DStream和一个RDD执行某些操作，但是DStream并没有直接提供这样的api供我们直接使用，在这种情况下就是transform的用武之地了。例：我们想把新接收的数据和之前计算好的DStream执行join操作，并执行filter操作，那么我们可以这么做：

```java
import org.apache.spark.streaming.api.java.*;
// RDD containing spam information
// 存在的RDD
final JavaPairRDD<String, Double> spamInfoRDD = jssc.sparkContext().newAPIHadoopRDD(...);

// wordCounts是DStream
JavaPairDStream<String, Integer> cleanedDStream = wordCounts.transform(
  new Function<JavaPairRDD<String, Integer>, JavaPairRDD<String, Integer>>() {
    @Override public JavaPairRDD<String, Integer> call(JavaPairRDD<String, Integer> rdd) throws Exception {
      // 和之前的RDD执行join操作然后做过滤
      rdd.join(spamInfoRDD).filter(...); // join data stream with spam information to do data cleaning
      ...
    }
  });
```

transform操作将被运用的每个时间片上的DStream上。也就说通过transform，我们可以间接调用没有暴露给DStream的api来实现某些操作。

#### 4.3 Window操作

Spark Streaming也提供了windowed计算操作，允许你在若干个DStream上组合操作并滑动操作。用语言不好表述，还是看下面的图吧。这个概念在结构化流中有更灵活的用处。

![spark_28](images\spark_28.png)

就如图中表示一样，每次window会在DStream上滑动若干个RDD的距离，而落在window中的源RDD会被组合被操作后产生一个新的叫做windowed的DStream。从图中展示的例子来看，window操作包含3个时间单位的RDD，每次会滑动两个时间单位。同时也展示了每个window操作必须要两个参数：

- window长度：window的时间单位长度（在图中是3个）
- sliding长度：每次window滑动的时间单位长度（在图中是2个时间单位）

这两个参数必须是时间片的整数倍。

下面我们通过一个例子来讲述一下window操作。假设，基于quick example中的例子，如果我们想实现在最近30秒中，每10秒内接受的单词数据，我们可以这么做：

```java
// Reduce function adding two integers, defined separately for clarity
Function2<Integer, Integer, Integer> reduceFunc = new Function2<Integer, Integer, Integer>() {
  @Override public Integer call(Integer i1, Integer i2) {
    return i1 + i2;
  }
};

// Reduce last 30 seconds of data, every 10 seconds
JavaPairDStream<String, Integer> windowedWordCounts = pairs.reduceByKeyAndWindow(reduceFunc, Durations.seconds(30), Durations.seconds(10));
```

常见的跟window相关的操作有：window、countByWindow、reduceByWindow、reduceByKeyAndWindow等。

#### 4.4 Join操作

流可以通过join同其他的流或者存在的DataFrame合并。

```java
JavaPairDStream<String, String> stream1 = ...
JavaPairDStream<String, String> stream2 = ...
JavaPairDStream<String, Tuple2<String, String>> joinedStream = stream1.join(stream2);
```

这里，stream1中的RDD将会和stream2中的RDD进行合并。我们也可以通过leftOuterJoin、rightOuterJoin和fullOuterJoin来实现左、左和全连接操作。

### 5. DStream的输出

DSteam的输出操作可以将DStream的数据推送到外部存储系统比如数据库、文件系统中存储。因为输出操作实际上是允许被transformed的数据被外部系统消费，所以这些输出操作实际上跟RDD的action操作一样会触发DStream的transformations操作的执行。目前DStream支持的输出操作有：print、saveAsTextFiles、saveAsObjectFiles、saveAsHadoopFiles和foreachRDD。

**关键点**

- DStream跟RDD一样也是懒执行的，也即是只有在有输出操作的时候才会执行transformation操作。特别的，DStream的输出操作内部是执行的RDD的action操作，并且对从输入流接收到的数据的处理也是有内部RDD的action操作来触发的。所以，如果我们DStream有输出操作，但是该输出操作的内部实现并没有RDD的action操作（比如foreachRDD操作），那么这样的输出操作将只是简单的接收数据而不会执行任何其他的操作。
- 默认情况下，输出操作会根据他们在程序中的定义顺序一个一个的执行完成。

### 6. Accumulators和Broadcast变量

Accumulator和Broadcast变量在Spark Streaming中不能从CheckPoint点恢复。所以如果你配置了checkpoint并且也要使用Accumulator和Broadcast变量，那么你的Accumulator和Broadcast变量必须以延迟加载的方式来创建，这样如果spark应用因为失败重启时可以重新初始化这些Accumulator和Broadcast变量。实例代码：

```java
class JavaWordBlacklist {

  private static volatile Broadcast<List<String>> instance = null;  // Broadcast变量

  public static Broadcast<List<String>> getInstance(JavaSparkContext jsc) {  // 使用的时候才初始化
    if (instance == null) {
      synchronized (JavaWordBlacklist.class) {
        if (instance == null) {
          List<String> wordBlacklist = Arrays.asList("a", "b", "c");
          instance = jsc.broadcast(wordBlacklist);
        }
      }
    }
    return instance;
  }
}

class JavaDroppedWordsCounter {

  private static volatile LongAccumulator instance = null;  // Accumulator变量

  public static LongAccumulator getInstance(JavaSparkContext jsc) { // 使用的时候才初始化
    if (instance == null) {
      synchronized (JavaDroppedWordsCounter.class) {
        if (instance == null) {
          instance = jsc.sc().longAccumulator("WordsInBlacklistCounter");
        }
      }
    }
    return instance;
  }
}

wordCounts.foreachRDD(new Function2<JavaPairRDD<String, Integer>, Time, Void>() {
  @Override
  public Void call(JavaPairRDD<String, Integer> rdd, Time time) throws IOException {
    // Get or register the blacklist Broadcast
    final Broadcast<List<String>> blacklist = JavaWordBlacklist.getInstance(new JavaSparkContext(rdd.context()));
    // Get or register the droppedWordsCounter Accumulator
    final LongAccumulator droppedWordsCounter = JavaDroppedWordsCounter.getInstance(new JavaSparkContext(rdd.context()));
    // Use blacklist to drop words and use droppedWordsCounter to count them
    String counts = rdd.filter(new Function<Tuple2<String, Integer>, Boolean>() {
      @Override
      public Boolean call(Tuple2<String, Integer> wordCount) throws Exception {
        if (blacklist.value().contains(wordCount._1())) {
          droppedWordsCounter.add(wordCount._2());
          return false;
        } else {
          return true;
        }
      }
    }).collect().toString();
    String output = "Counts at time " + time + " " + counts;
  }
}
```

### 7. DataFrame和SQL操作

在我的另一篇文章：结构化流中介绍。

### 8. Caching/Persistence

跟RDD一样，DStream也允许开发者将流数据保存在spark内存中。也就是说，在DStream上调用persist()方法将会自动将数据保存在内存中，这在数据要被多次使用的情况下时非常有用的。针对DStream的window操作，比如reduceByWindow、reduceByKeyWindow和基于状态的updateStateByKey类型的操作，更加有用。所以在Spark中通过window类操作得到的DStream将会自动保存在内存中，而不需要我们手动调用persist()方法。

而针对从网络比如Kafka、Flume、socket等源接收到的数据，默认的persist等级是保存两个副本在两个节点上。

要注意的是，DStream和RDD两者默认的存储等级是不一样的，RDD默认是只存在内存中。

### 9. Checkpointing

一个Streaming应用是24小时不停歇工作的，所以针对一些非逻辑错误比如系统错误、jvm错误等必须要能够自动重启恢复。为了达到这个目的，Spark Streaming就必须要checkpoint足够多的信息来使得应用能够在重启之后恢复到出错时的运行状态。

checkpoint涉及到两种类型的数据：

- 元数据的checkpoint：
  - 配置信息：创建Streaming应用的配置数据
  - DStream的操作：DStream的操作集合
  - Incomplete batches（没有接收完的数据）：负责接收数据的作业已经加入执行队列，但是还没有执行完成
- 数据的checkpoint

**什么时候要使用checkpoint**

在下列情况中可以在应用中使用checkpoint：

- 状态转换使用时：如果在应用有updateStateByKey或者reduceByKeyAndWindow操作，那么可以使用checkpoint
- 想要失败恢复时。

**checkpoint怎么用**

checkpoint这个功能的使用很简单，通过设置一个保存checkpoint信息的可靠的目录即可。代码中通过streamingContext.checkPoint(checkPointDirectory)来设置，除此之外，如果你想要你的应用可以失败恢复，那么你必须要重写你的应用，使他拥有如下行为：

- 当应用第一次启动时，它会创建一个新的StreamingContext对象，设置好所有的输入流并调用start（）；
- 当应用因为失败重新启动时，它会根据checkpoint目录中保存的数据重新来创建StreamingContext对象。

这些行为可以很简单的通过JavaStreamingContext.getOrCreate()方法实现，如下：

```java
// Create a factory object that can create and setup a new JavaStreamingContext
JavaStreamingContextFactory contextFactory = new JavaStreamingContextFactory() {
  @Override public JavaStreamingContext create() {
    JavaStreamingContext jssc = new JavaStreamingContext(...);  // new context
    JavaDStream<String> lines = jssc.socketTextStream(...);     // create DStreams
    ...
    jssc.checkpoint(checkpointDirectory);                       // set checkpoint directory
    return jssc;
  }
};

// Get JavaStreamingContext from checkpoint data or create a new one
JavaStreamingContext context = JavaStreamingContext.getOrCreate(checkpointDirectory, contextFactory);

// Do additional setup on context that needs to be done,
// irrespective of whether it is being started or restarted
context. ...

// Start the context
context.start();
context.awaitTermination()
```

如果checkpointDirectory存在了，那么context将会从checkpointDirectory目录中的数据重新创建。如果这个目录不存在（第一次运行），那么contextFactory就会被调用来创建一个新的context。

### 10. 部署应用

为了运行一个Spark Streaming应用，我们必须有如下准备：

- *Cluster with a cluster manager* - 这是所有Spark应用最基础的需求；
- *Package the application JAR* - 我们必须把自己的应用打包成jar包，如果我们使用spark-submit命令来提交运行我们的应用，那么可以不需要Spark和SparkStreaming的jar包。但是，如果我们的应用使用了advanced sources，那么我们必须要把相关的jar打包到我们的应该包里面。比如，如果我们是读取kafka、flume等数据源，那我们就需要把对应的spark-streaming-kafka-*.jar等包打入到我们最终的jar包中；
- *Configuring sufficient memory for the executors* - 因为接受的数据必须要存放在内存里面，所以每个executor都要配置足够的内存来存放这些数据；特别的，如果是进行window类的操作，可能会存储最近几十秒或者几分钟内接受到的数据，这就要求有更大的内存，所以分配的内存大小跟你要执行的操作类型有关；
- *Configuring checkpointing* - 如果我们的应该需要这个目录，那我们也要指定一个Hadoop类的文件目录用于存放checkpoing的数据；
- *Configuring automatic restart of the application driver* - 配置失败自动重启机制，针对不同的集群管理架构有不同的实现方式；
- *Configuring write ahead logs* - 从Spark1.2开始，我们就开始引入了wal方式来实现强容错机制。如果开启这个功能，那么所有接收到的数据都会写入到配置的checkpoint目录的wal日志文件中，这可以保证在driver重启过程中数据不会丢失，这个功能可以通过配置参数spark.streaming.receiver.writeAheadLog.enable=true来开启，写入wal日志文件会造成系统吞吐增大。此外，如果我们开启了wal功能，那我们可以把接收数据复制到两个executor的功能给关闭，因为wal已经是存放在一个分布式文件系统中了（checkpoint目录是一个hadoop类的文件系统目录）具有了高容错性，关闭复制可以通过设置存储等级为StorageLevel.MEMORY_AND_DISK_SER来实现。
- *Setting the max receiving rate* - 如果集群资源处理接收数据的能力达不到接收数据的速度，那么有必要控制一个最大的接收数据速度，可以通过spark.streaming.receiver.maxRate来设置；在Spark1.5中，已经引入了一种叫*backpressure*的机制，这种机制可以根据集群处理数据的速度自动调节接收数据的速率，这个机制可以通过设置配置文件中的spark.streaming.backpressure.enabled来控制。

### 11. 性能调节

为了得到Spark Streaming应用的最佳性能，做一些调节是必须的。这一部分主要介绍和解释一个参数和配置，通过调节这些参数和配置可以提高我们应用的执行效率。在一个大的层面上，我们需要考虑两件事：

1. 怎样减少每个接收数据块的批处理时间；
2. 怎么设置一个对的时间间隔，使得每个时间间隔接收到的数据块能被更快的处理。

#### 11.1 减少批处理时间

有很多优化方式可以减少批处理的时间，这些在性能调节指导这篇文章(我暂时没写，只能去官网查看了)中有详细的介绍。这里将介绍几个重要的方式。

**调整数据接收并行化**

通过网络从数据源(Kafka,Flume,socket等)接收的数据需要被反序列化存储到Spark中。如果数据接收成为了系统的瓶颈，那么我们可以考虑多路并行接收数据。值得注意的是，在Spark中每个Input DStream只创建一个Receiver(运行在一个worker节点上)来接收单个流的数据。因此接收多个流的数据可以通过创建多个Input DStream，每个Input DStream接收不同的数据源数据就可以实现接收多源数据了。例如，单个Input DStream从Kafka接收两个topic的数据可以分成两个Input DStream分别接收不同topic的数据，这样两个Input DStream就会分别创建一个Receiver来同时接收数据，从而可以提高整个系统的吞吐量。这个多个Input DStream可以通过unin方式合并成一个DStream，然后针对这一个DStream可以执行其他的操作，示例如下：

```java
int numStreams = 5;
List<JavaPairDStream<String, String>> kafkaStreams = new ArrayList<>(numStreams);
for (int i = 0; i < numStreams; i++) {
  kafkaStreams.add(KafkaUtils.createStream(...));
}
JavaPairDStream<String, String> unifiedStream = streamingContext.union(kafkaStreams.get(0), kafkaStreams.subList(1, kafkaStreams.size()));
unifiedStream.print();
```

另一个可调节参数是receiver的**blocking interval**，这个参数可以通过配置spark.streaming.blockInterval调节。对Spark中的大多数Receiver来说，接收到的数据在被存放到Spark的内存之前会根据这个block interval设置的时间被聚合成一个block，DStream中的每个batch(就是RDD)中的block数就决定了处理接收数据的任务数，说简单点就是一个DStream由RDD(这里是batch)组成，每个RDD由partition(这里是block)组成，而partition是创建任务的基本数据单元，所以block数决定了处理接收数据的任务数。如果任务数太低，那么集群空闲资源会很多造成集群的资源浪费，所以调整这个数值能很好的控制集群资源的利用率，blocking interval的最小建议值是50ms，低于这个值任务数就会增大可能造成集群资源过度消耗。

**调整数据处理并行化**

由于系统并行任务数不够高，可能会造成集群资源没有被完全利用起来。比如，针对分布式reduce操作，像reduceByKey和reduceByKeyAndWindow，默认的并行任务数是通过spark.defaul.parallelism这个参数控制的，我们可以调整这个参数值来修改任务的并行度。

**数据序列化**

调整序列化格式可以减少数据序列化的开销。在Spark Streaming中，有两种类型的数据需要被序列化。

- **Input Data**：默认情况下，Receiver接收的输入数据以StorageLevel.MEMORY_AND_DISK_SER_2的存储方式存放在Spark集群的executor的内存中，也就是说输入数据被序列化来减少GC开销，存储一个副本来增强容错性。显然数据序列化也会有开销-Receiver必须把接收的输入数据反序列化，然后以Spark中的序列化格式重新序列化输入数据。
- **Persisted RDDs generated by Streaming Operations**：在流处理计算过程中产生的RDDs可能会持久化存储在内存中。比如window类操作就是这样。然后不像Spark核心中RDD默认持久化机制是StorageLevel.MeMORY_ONLY，在Spark Streaming的默认持久化机制是StorageLevel.MEMORY_ONLY_SER。

在上面两种情况中，使用**Kryo**序列化方式可以有效的减少CPU和内存的开销。在某些情况下，当Spark Streaming需要保存的数据不是太大时，不序列化数据存储也是个不错的选择，可以有效的减少GC开销。例如，如果我们设置的batch interval时间非常小并且也没有window操作，那么我们可以尝试设置StorageLevel来取消数据序列化，这样会减少因为序列化而增加的额外CPU开销，从而提高执行效率。

**任务加载开销**

如果每秒钟加载的任务数太高(比如说50或者更高)，那么将任务分发到workers节点就可能很难实现毫秒级的延迟，像这样的开销可以通过下面的方式改进：

- **Execution mode**：Spark以Standalone或者是Mesos的coarse-grained模式相比于Mesos的fine-grained模式在任务加载表现上具有更好的表现。

#### 11.2 设置正确的批处理时间

对一个稳定的Spark Streaming应用来说，整个系统处理数据的速度应该要快于数据被接收的速度。用另一句话说，一个数据批处理块被处理掉的时间应该比他生成的时间小。所以一个batch interval时间间隔的设置将起着很重要的影响，比如上面的WordCountNetwork例子，针对设置的2秒时间内的处理系统表现很好，在2秒内接受的数据能够被快速的处理，而如果我们设置成其他的时间值，那么就可能造成系统延迟慢慢变长。

#### 11.3 内存调节

调整内存信息和GC行为在性能调节指导文档中有详细的讨论，强烈推荐大家读一读那篇文档。这本部分，我们会讨论对Spark Streaming应用有用的一些参数指标。

Spark Streaming应用所需要的内存大小依赖于我们的Spark Streaming应用需要执行哪些类型的transformation算子。比如，如果你想用window操作来处理最后10分钟的数据，那么我们集群就必须有能力容纳这10分钟的数据的内存；相反，如果我们只是做一些简单的map-filter-store类的算子操作，那需要的内存就相对小很多。

一般情况下，因为Receiver接受的数据是以StorageLevel.MEMORY_AND_DISK_SER_2存储机制存放到executor内存中，当内存不够时多余的数据会写到磁盘上，这就会降低应用的性能，所以提供足够的内存是有必要的。

内存调节的另一方面的垃圾回收，因为Spark Streaming操作需要低延迟特性，所以如果因为GC回收引起系统的暂停是不被待见的。

下面几个参数能够帮助我们调节内存和GC的开销：

- **Persistence Level of DStreams**：正如在上面数据序列化部分提到的，接收的数据和RDDs默认是序列化方式存储在内存中的，相比无序列化可以减少内存占用和GC开销，使用Kryo序列化方式能更好的减小序列化后的数据大小和内存占用；更有甚的我们可以使用压缩方式(Spark配置参数：spark.rdd.compress)以消耗CPU方式来减少内存占用。
- **Clearing old data**：默认所有的接收数据和持久化RDDs都会被自动的清理，spark会根据transformation算子来决定什么时候清理数据，通过设置streamingContext.remember可以设置数据的保留时长。
- **CMS Garbage Collector**：使用并行标记清楚GC方式来回收垃圾是强烈推荐，设置CMS GC可以通过在spark-submit命令中设置--driver-java-options参数来之driver节点，在配置文件中配置spark.executor.extraJavaOptions参数来设置executor的回收方式。
- **其他建议**：为了减少GC开销，还有其他一些建议
  - 持久化RDDs时使用OFF_HEAP机制
  - 使用更多比较小heap size的executor

**需要记住的重点**

- 一个DStream只和一个Receiver关联。为了实现多个Receiver并行化读取接收数据，就必须创建多个DStream。一个Receiver运行在一个executor里面，占用一个CPU核，要确保分配足够多的CPU核来接收和处理数据。一个Receiver以哈希轮询的方式被分配到executor上。
- 当数据被接收之后，Receiver会创建数据block(注意是block不是batch)，每个一个blockInterval时间会创建一个新的dataBlock。在一个batchInterval中会创建N个dataBlock，其中N=batchInterval/blockInterval。这个blocks会由executor上的BlockManager模块负责分布到集群中其他的executor中的blockManager上去，然后，将各个block的位置信息通知运行在driver节点上的Network Input Tracker模块。
- 一个RDD就是基于一个batchInterval中创建的blocks而创建的。在一个batchInterval期间创建的blocks就对应成为了RDD中的partition。在Spark中每个partition就是一个任务。所以如果batchInterval=blockInterval就意味着一个RDD中就只有一个partition。
- 在每个blocks上的map任务会在包含这个block的executor中执行，除非任务没有被分配到数据所在的executor节点上。blockinterval越大意味着block越大，在配置文件中增大spark.locality.wait参数值可以增大数据在本地处理的几率，也就是减少了数据传输的网络I/O，所以在blockInterval和wait参数之间要找到一个平衡，使得数据能够尽量在本地被处理。
- 除了依赖于batchinterval和blockinterval，我们可以通过inputDstream.repartition(n)方法来重新定义RDD的partition数，虽然这样会增加shuffle的开销。
- 如果你有两个DStream那么将会有两个RDD就可能会创建两个job有driver调度，为了避免这种情况，你可以合并两个DStream，这样将确保只形成一个unionRDD，这样这个unionRDD就会被创建为一个Job。
- 如果batch处理时间大于batchInterval的时间，那么显然Receiver的内存会慢慢被填满直到抛出exception(最可能是BlockNotFoundException)，目前没有方法可以停止Receiver继续接受数据，不过可以通过spark.streaming.receiver.maxRate参数来限制Receiver的接收速度。