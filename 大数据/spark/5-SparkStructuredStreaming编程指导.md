## Spark2.0结构化流编程指导

### 1. Overview

​	Spark结构化流时构建在SparkSQL引擎之上的一个可扩展性、容错的流处理引擎。让我们可以用计算静态数据集一样的方式来表达我们的流计算。SparkSQL引擎将会处理从数据流持续获取到的数据进行增量处理，我们可以使用SparkSQL中的Dataset/DataFrame API来做流聚合、windows操作、join操作等。最后，Spark系统会通过checkpointing和Write Ahead Logs的方式来确保应用执行的准确和错误恢复。

​	在Spark2.0版本中，结构化流任然是试用阶段。在本篇文章中，我们将会讨论结构化流的编程模型和它相关的API。

### 2. Quick Example

​	下面还是以计算单词数量为例，来说明结构化流的处理过程。在运行这个例子之前，我们必须要有一个提供流的服务器，在linux中我们可以使用Netcat这个网络工具来提供这一功能，关于Netcat的使用请参考我的相关文章。例子代码如下：

```java
package com.shell.hadoop.spark.sql.streaming;

import java.util.Arrays;
import java.util.Iterator;

import org.apache.spark.api.java.function.FlatMapFunction;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Encoders;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.SparkSession;
import org.apache.spark.sql.streaming.StreamingQuery;

public class StructuredNetworkWordCount {
	public static void main(String[] args) {
		
		if (args.length < 2) {
			System.err.println("Usage: StructuredNetworkWordCount <hostname> <port>");
			System.exit(1);
		}
		
		String host = args[0];
		int port = Integer.parseInt(args[1]);
		
		SparkSession spark = SparkSession.builder().appName("StructuredNetworkWordCount").getOrCreate();
		
		Dataset<String> lines = spark.readStream().format("socket").option("host", host).option("port", port).load().as(Encoders.STRING());
		
		Dataset<String> words = lines.flatMap(new FlatMapFunction<String, String>() {

			@Override
			public Iterator<String> call(String t) throws Exception {
				return Arrays.asList(t.split(" ")).iterator();
			}
			
		}, Encoders.STRING());
		
		Dataset<Row> wordCounts = words.groupBy("value").count();
		
        // start之后开始接收数据
		StreamingQuery query = wordCounts.writeStream().outputMode("complete").format("console").start();
		query.awaitTermination();
	}
}
```

​	在上面的例子中，lines这个DataFrame表示的是一个没有边界的table，从流中接收的数据不断的插入table中。这个table会包含一个列名叫做**value**的列，从流中接收的每一行数据会插入table中成为table中的row。在定义lines这一步跟Spark中其他计算模型一样，并不会马上接收数据，也要得到start()执行时才开始接收数据。当我们在流上设置了一个查询query，并启动start之后，应用就会开始接收数据并计算值。

### 3. Programming Model - 编程模型

​	理解结构化流的关键点是：将一个实时数据流作为一个不断在后面拼接数据的table。这就产生了一个和批处理模型很相似的流处理模型。下面让我们理解这个模型的详细内容。

#### Basic Concepts

​	将输入数据流作为一个输入表，每一个从输入流接收的数据项将作为一个新的行插入到输入表的后面。 ![spark_30](images\spark_30.png)

​	针对输入流的查询将会生成一个结果表。在每一个触发点上（输入时间片决定，假设为1秒），一个新的行就会被拼接到输入表后面，最后会反应到结果表的结果上，并最终写入到外部存储介质上面。 ![spark_31](images\spark_31.png)

​	图中的Output表示最终的存储介质，结构的输出保存有三种不同的模式：

- Complete Mode：整个被更新的结果表会被写入到外部存储介质上，至于写表的过程有存储连接器决定

- Append Mode：只有在上次结果表之后被新插入结果表的数据会被写入到外部存储介质上，这只有在结构表中已存在的结果不会被修改的情况下使用

- Update Mode：只有在上次结果表之后被更新过的数据会被写入到外部存储介质上（在Spark2.0中赞不支持这个模式）

需要注意的是，每种模式适用于不同类型的查询场景。

​	以上面的代码为例来描述整个的流程。第一个**lines** DataFrame是我们上面提到的输入表，用来接收输入数据，最后的**wordCounts** DataFrame是上面所说的结果表。注意从lines DataFrame 到生成最后的wordCounts Dataframe这个过程跟SparkSQL中的静态数据处理过程一样。当我们创建查询对象**query**并调用start方法后，Spark就开始不断从socket连接那个检测新的输入数据，当有新的数据时，Spark会运行一个增量查询，将之前的counts运行结果和新的数据组合来计算出新的结果，流程如下图： ![spark_32](images\spark_32.png)

​	这个流处理模型跟其他流处理模型的很大的不同点在于，针对新接收的数据不需要我们自己去处理维护，Spark会帮我们将新数据到来后的处理结构反应到结果表中。

### 4. Handling Even-time and Late Data

​	**Event-time**是嵌入在数据本身的一个时间值。对许多应用，你可能想要知道这个**Event-time**并操作他们。例如，如果我们想要知道IoT设备每分钟发生的events值，那么你可能想要使用数据的生成时间，而不是Spark接收到数据的时间值。这个event-time特性在此编程模型中是很自然的特性，每一个从设备接收的event是输入表中的一行，而event-time则是行中的某个列值。就是这个特性，使得基于windows操作的分组聚合操作很容易，这在下面的**window operations**部分详细介绍。

### 5. Fault Tolerance Semantics

​	Spark结构化流的一个设计目标是实现end-to-end exactly-once容错性（只接收一次数据，并保证数据零丢失，并且可以失败重启）。为了达到这个目标，我们设计Structured Streaming sources，sinks和执行引擎可以可靠精确的跟踪应用的处理过程，让应用可以通过restart/reprocessing处理任何类型的失败。每一个输入流都被假定有一个偏移量让应用可以跟踪读取位置（replayable sources），Spark引擎会使用checkpointing和write ahead logs来记录这个偏移量。sinks被设计为幂等重处理的（idempotant sinks）（这个我也不懂，^_^）。replayable sources和idempotant sinks的结合使用，确保了Spark结构化流的end-to-end exactly-once这个特性。

### 6. 使用Datasets和DataFrames API

​	从Spark2.0开始，DataFrames和Datasets既可以表示静态的，有边界的数据，也可以用来表示流式的，无边界的数据。跟静态Datasets/DataFrames一样，我们也可以通过SparkSession来创建流式的Datasets/DataFrames，并可以执行一样的操作。如果对Datasets/Dataframes不熟悉，请移步：**SparkSQL编程指导**。

#### 创建流式Datasets和DataFrames

​	Streaming DataFrames可以通过由SparkSession.readStream()方法返回的DataStreamReader接口创建。跟通过SparkSession.read()方法返回的接口创建的静态DataFrame一样，我们也可以给流式DataFrame指定数据格式，访问模式，其他配置项等。在Spark2.0中，支持下面几种内置的流：

- File source：读取提供目录中的文件数据作为流式数据源。支持的文件格式有：text、csv、json和parquet
- Socket source（for testing）：从一个socket连接读取UTF-8编码的文本数据，注意这个应该只用于测试目的，因为这种数据源不能提供end-to-end exactly-once的容错保证。

这里有一些例子：

```java
SparkSession spark = ...

// Read text from socket 
Dataset<Row> socketDF = spark
    .readStream()
    .format("socket")
    .option("host", "localhost")
    .option("port", 9999)
    .load();

socketDF.isStreaming();    // Returns True for DataFrames that have streaming sources

socketDF.printSchema();

// Read all the csv files written atomically in a directory
StructType userSchema = new StructType().add("name", "string").add("age", "integer");
Dataset<Row> csvDF = spark
    .readStream()
    .option("sep", ";")
    .schema(userSchema)      // Specify schema of the csv files
    .csv("/path/to/directory");    // Equivalent to format("csv").load("/path/to/directory")
```

​	上面的例子生成的流式DataFrame都是无类型的，意味着在编译时是不知道DataFrame的schema的，只会在query start的时候会检测。一些操作，比如map、flatmap等，需要在编译时知道类型，所以如果应用中有这些操作，需要把这些无类型的流式DataFrames转换为有类型的Datasets。至于怎么转换，请移步：**SparkSQL编程指导**。

#### 流式DataFrames/Datasets的操作

​	我们可以运用各种类型的操作在流式DataFrames/Datasets上面，从无类型的，支持SQL的（比如select、where、groupby）到有类型的类RDD的操作（map、filter、flatMap等）。

**基本操作-Selection，Projection，Aggregation**

流式DataFrames/Dataset支持许多常见的操作。一些不支持的操作会在后面部分提到。

```java
import org.apache.spark.api.java.function.*;
import org.apache.spark.sql.*;
import org.apache.spark.sql.expressions.javalang.typed;
import org.apache.spark.sql.catalyst.encoders.ExpressionEncoder;

public class DeviceData {
    private String device;
    private String type;
    private Double signal;
    private java.sql.Date time;
    ...
    // Getter and setter methods for each field
}

Dataset<Row> df = ...;    // streaming DataFrame with IOT device data with schema { device: string, type: string, signal: double, time: DateType }
Dataset<DeviceData> ds = df.as(ExpressionEncoder.javaBean(DeviceData.class)); // streaming Dataset with IOT device data

// Select the devices which have signal more than 10
df.select("device").where("signal > 10"); // using untyped APIs
ds.filter(new FilterFunction<DeviceData>() { // using typed APIs
  @Override
  public boolean call(DeviceData value) throws Exception {
    return value.getSignal() > 10;
  }
}).map(new MapFunction<DeviceData, String>() {
  @Override
  public String call(DeviceData value) throws Exception {
    return value.getDevice();
  }
}, Encoders.STRING());

// Running count of the number of updates for each device type
df.groupBy("type").count(); // using untyped API

// Running average signal for each device type
ds.groupByKey(new MapFunction<DeviceData, String>() { // using typed API
  @Override
  public String call(DeviceData value) throws Exception {
    return value.getType();
  }
}, Encoders.STRING()).agg(typed.avg(new MapFunction<DeviceData, Double>() {
  @Override
  public Double call(DeviceData value) throws Exception {
    return value.getSignal();
  }
}));
```

**基于Event-time的Window操作**

​	基于sliding window（在SparkStreaming编程指导的文章中有提过）的聚合操作在结构化流处理中是很直接的。其实基于window的聚合操作跟我们在关系型数据中的分组聚合是很相似的，只不过在分组聚合中，我们计算聚合值（比如counts）是针对的用户指定的group条件中列的唯一值次数，而在基于window的聚合操作中，聚合值是通过落在window中的行数来计算的，下面将通过详细的例子来解说。

​	想象一下，我们quick example中的程序要修改成这样子的：由流接收的数据现在会包含一个时间值，然后我们想计算每过5分钟间隔，取10分钟的时间片长度的数据计算结果。也就是说，接收到的单词在时间段12:00 - 12:10，12:05 - 12:15,12:10 - 12:20 等中的每个单词出现次数，其中12:00 - 12:10表示在12:00之后12:10之前生成的数据。现在，考虑这样一个情况，一个单词在12:07被生成，那么这个增量数据会落在12:00 - 12:10和12:05 -12:15两个windows上，这个结果表看起来如下图： ![spark_33](images\spark_33.png)

因为window操作类似于分组操作，所以在代码里面，我们可以使用groupBy()和window()操作来表示window聚合操作，上面图的代码如下：

```java
Dataset<Row> words = ... // streaming DataFrame of schema { timestamp: Timestamp, word: String }

// Group the data by window and word and compute the count of each group
Dataset<Row> windowedCounts = words.groupBy(
  functions.window(words.col("timestamp"), "10 minutes", "5 minutes"),
  words.col("word")
).count();
```

​	现在我们考虑这样一种情况，比如：一个单词在服务端是12:04分生成的，但是当他被spark接收时是12:11分，那么spark是怎么计算的呢？我们要明白的一点是，因为Spark的window是基于数据里的时间，而不是接收数据的时间，所以12:04将作为这个数据的时间，用图来描述如下： ![spark_34](images\spark_34.png)

**Join操作**

Streaming DataFrames可以和静态的DataFrames进行合并。

```java
Dataset<Row> staticDf = spark.read. ...;
Dataset<Row> streamingDf = spark.readStream. ...;
streamingDf.join(staticDf, "type");         // inner equi-join with a static DF
streamingDf.join(staticDf, "type", "right_join");  // right outer join with a static DF
```

### 7. 不支持的操作

​	在spark目前的版本中（2.0），流式DataFrames不支持的操作如下：

- 多个流的聚合
- Limit和take若干个行
- Distinct
- Sorting不支持
- Outer join，在一个流式DataFrames跟静态DataFrames有条件的支持：
  - 和流式Dataset做Full outer join是不支持的
  - 当右边是流式DataFrames时，做Left outer join是不支持的
  - 当左边是流式DataFrames时，做Right outer join是不支持的
- 在两个流式DataFrames之间，任何类型的join操作都是不支持的
- count() - 不会从流式Dataset返回一个单一的count值，使用dataset.groupBy.count()代替
- foreach() - 使用dataset.writeStream.foreach()代替
- show() - 使用format("console")代替

### 8. Starting Streaming Queries

​	当我们已经定义好了最终的结果DataFrames/Dataset之后，我们剩下的事情就是要启动开始我们的流式计算，为了这个目的，我们必须使用由Dataset.writeStream()返回的接口**DataStreamWriter**，并且必须为DataStreamWriter赋予一个或多个如下的操作：

- Details of the output sink：数据的输出格式和输出位置等
- Output mode：上面提到过的，**append**，**complete**和**update**（2.0赞不支持）
- Query name：可选的，赋予一个唯一的名字来区分这个查询操作
- Trigger interval：可选的，指定触发间隔。如果没有指定这个值，Spark系统会在前一次处理进程结束时快速检测是否有新数据，如果前一次的处理进程没有完成，那么spark会在下次的触发点触发，不会再这个处理进程完成之后马上处理（这里写的有点奇怪，难道会有一个系统默认的触发时间间隔么，那他文档中又怎么不说呢？**奇怪，奇怪**）
- Checkpoint location：指定一个兼容HDFS API的文件系统路径，用于保存Checkpoint的信息

**Output Modes**

目前实现的输出模式有两种：

- Append mode：默认的输出模式，只有在上一次结果表之后被添加到结果表中的数据会被输出，这种模式只适合没有任何聚合操作的应用，比如只有（select，where，map，flatMap，filter，join等）
- Complete mode：整个结果表全部输出，适合用在有聚合操作的过程中

**Output Sinks**

目前支持的内置output sink有：

- File sink：将output输出保存在一个目录中，到Spark2.0，只支持parquet文件格式和append输出模式
- Foreach sink：在output输出的记录上执行任意的计算，下面有讲
- Console sink：调试时用，将输出打印到标准输出中。Append和Complete两种输出模式都支持
- Memory sink：调试时用，输出作为一个内存表保存在内存中。Append和Complete两种输出模式都支持。

下面是一张表格：

| Sink                                     | Supported Output Modes | Usage                                    | Fault-tolerant                          | Notes                                    |
| ---------------------------------------- | ---------------------- | ---------------------------------------- | --------------------------------------- | ---------------------------------------- |
| **File Sink**(only parquet in Spark 2.0) | Append                 | writeStream.format("parquet").start()    | Yes                                     | Supports writes to partitioned tables. Partitioning by time may be useful. |
| **Foreach Sink**                         | All modes              | writeStream.foreach(...).start()         | Depends on ForeachWriter implementation |                                          |
| **Console Sink**                         | Append, Complete       | writeStream.format("console").start()    | No                                      |                                          |
| **Memory Sink**                          | Append, Complete       | writeStream.format("memory").queryName("table").start() | No                                      | Saves the output data as a table, for interactive querying. Table name is the query name. |

最后，你必须要调用start()方法来开始执行，这将返回一个StreamingQuery对象，我们可以通过这个对象来管理这个查询。

下面我们用一些代码例子来理解上面所讲的东西：

```java
// ========== DF with no aggregations ==========
Dataset<Row> noAggDF = deviceDataDf.select("device").where("signal > 10");

// Print new data to console
noAggDF
   .writeStream()
   .format("console")
   .start();

// Write new data to Parquet files
noAggDF
   .writeStream()
   .parquet("path/to/destination/directory")
   .start();
   
// ========== DF with aggregation ==========
Dataset<Row> aggDF = df.groupBy("device").count();

// Print updated aggregations to console
aggDF
   .writeStream()
   .outputMode("complete")
   .format("console")
   .start();

// Have all the aggregates in an in-memory table 
aggDF
   .writeStream()
   .queryName("aggregates")    // this query name will be the table name
   .outputMode("complete")
   .format("memory")
   .start();

spark.sql("select * from aggregates").show();   // interactively query in-memory table
```

**使用Foreach**

​	foreach操作允许在输出数据上执行操作。为了使用foreach操作，我们必须要实现ForeachWriter这个接口，接口里面的方法会在生产输出行时被调用。TODO。

### 9. Managing Streaming Queries

StreamingQuery对象在一个query start的时候被创建，通过这个StreamingQuery对象我们可以监控和管理这个query。

```java
StreamingQuery query = df.writeStream().format("console").start();   // get the query object

query.id();          // get the unique identifier of the running query

query.name();        // get the name of the auto-generated or user-specified name

query.explain();   // print detailed explanations of the query

query.stop();      // stop the query 

query.awaitTermination();   // block until query is terminated, with stop() or with error

query.exception();    // the exception if the query has been terminated with error

query.sourceStatus();  // progress information about data has been read from the input sources

query.sinkStatus();   // progress information about data written to the output sink
```

在同一个SparkSession中，我们可以start多个query。这多个query会并行运行。我们可以通过sparkSession.streams()方法得到StreamingQueryManager对象来管理这些并行的query。

```java
SparkSession spark = ...

spark.streams().active();    // get the list of currently active streaming queries

spark.streams().get(id);   // get a query object by its unique id

spark.streams().awaitAnyTermination();   // block until any one of them terminates
```

### 10. 使用checkpoint执行失败恢复

​	在失败或者有意关闭应用的情况下，我们可以恢复之前的处理过程和查询的执行状态，并继续执行。这个过程通过checkpoint和write ahead logs完成。我们可以配置一个查询的checkpoint地址，这样这个query会保存所有的处理信息和正在执行的聚合操作信息到checkpoint指定的地址。对于Spark2.0，checkpoint地址必须是一个兼容HDFS API的文件系统。设置这个地址的代码如下：

```java
aggDF
  .writeStream()
  .outputMode("complete")
  .option("checkpointLocation", "path/to/HDFS/dir")
  .format("memory")
  .start();
```

