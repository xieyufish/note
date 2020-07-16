## Spark2.0 Programming Guide(Spark2.0编程指导)

[TOC]

### 1. Overview-概览

​	每一个Spark应用都是由包含一个main方法的*driver program*组成，并且能够在一个集群上执行一系列的并行操作。Spark的第一个主要抽象概念是**RDD(Resilient distributed dataset):弹性分布式数据集**-分布在集群的各个节点上能够被并行操作的被分割好的数据集。RDD初始化可以由在hdfs（或其他hadoop支持的文件系统）上的文件或者是*driver program*中的一个集合来创建，用户可以在内存中persist一个RDD来允许它被重复高效的使用，RDD具有自动恢复容错能力。

​	Spark的第二个抽象概念是：**共享变量**。共享变量可以在并行操作中被使用。默认情况，Spark通过在不同的节点以任务集的方式来运行并行操作函数，spark会把在并行操作中用到的变量传递到每个节点上。有时，一个变量需要在不同的任务之间共享，或者在任务与主程序*driver program*之间共享。Spark支持两种类型的共享变量：**广播变量(broadcast variables)**-会在所有的节点上缓存该值；**accumulators**-可进行叠加操作的变量，比如计数和求和变量。

### 2. Resilient Distributed Datasets(RDDs)

​	RDD的概念贯穿于Spark的整个生态系统理论中，RDD是一个以并行方式运行具有容错性的数据元素的集合。在Spark中有两种方式来创建RDD数据集：**并行化集合**- parallelizing一个在*driver program*中定义的数据集合；**外部数据集**-指向引用一个外部存储系统中的数据集，比如一个共享文件系统上的文件、HDFS、HBase或者其他提供了Hadoop InputFormat特性接口的任意数据源。

#### 2.1 并行化集合-Parallelized Collections

​	并行化集合通过在一个java或者scala集合上调用**JavaSparkContext**的**parallelize**方法来创建。集合的元素被复制来生成一个可并行操作的分布式数据集。以下是一个创建并行化集合的样例：

```java
List<Integer> data = Arrays.asList(1, 2, 3, 4, 5);
JavaRDD<Integer> distData = sc.parallelize(data);
```

创建完成，分布式数据集distData就可以被并行操作。比如，我们可以调用distData.reduce((a,b) -> a + b)来计算集合的元素和。

​	并行化集合还有一个重要的参数是把一个集合切分成多少个partitions。Spark会在每个partition上运行一个任务。典型的在集群的每个CPU核上会分配2-4个partitions，也就意味着给每个CPU核分配2-4个任务。Spark会根据配置自动把一个集合切分成多少个partition，我们也可以自己通过调用**parallelize(data, 10)**这个方法来手动设置你想切分的partition数。

#### 2.2 外部数据集-External Datasets

​	Spark可以从任何Hadoop支持的存储源创建分布式数据集；包括本地文件系统、HDFS、HBase、Cassandra、Amazon S3等等。Spark支持文本文件、序列化文件和其他任何Hadoop支持的**InputFormat**格式。

​	文本文件的RDD可以使用**SparkContext的textFile**方法来创建。这个方法根据提供的文件URI(可以是一个本地路径或者是hdfs://, s3n://等形式的URI)将文件内容读取为文件中每个行的集合。下面是一个样例：

```java
JavaRDD<String> distFile = sc.textFile("data.txt");
```

创建完成，distFile就可以执行数据集的操作。比如：我们可以计算所有行的sizes：distFile.map(s -> s.length()).reduce((a, b) -> a + b)。

**Spark读取文件需要注意的：**

- 如果使用本地文件系统路径，那么这个文件必须是要所有节点可访问的，可以拷贝这个文件到所有的节点或者是通过网络挂载方式挂到一个共享文件系统上。
- Spark支持的文件输入方式：文本文件，目录文件，压缩文件，以及通配符文件。例如：你可以使用textFile("/my/directory"), textFile("/my/directory/\*.txt"), txtFile("/my/directory/*\*.gz")。
- textFile方法同样也支持一个可选的第二个参数来控制partitions的数目。默认的，Spark给每个文件块(HDFS中的文件分块)创建一个partition，当然你也可以通过传递一个更大的值来要求更多的partitions。但是partitions的数量不能够比blocks的数量少。

### 3. RDD操作-RDD Operations

​	RDDs支持两种类型的操作：**transformations(转换)** - 从一个存在的RDD上创建一个新的RDD；**actions(动作)** - 在RDD上执行一个计算操作之后返回一个值给*driver program*。例如，map是一个transformation操作，将数据集传递给一个函数并返回一个新的RDD结果；reduce是一个action操作，使用函数操作RDD的所有元素并返回一个最终的结果给*driver program*。

​	Spark中所有在RDD上的**transformations**操作都是懒惰的，也就是说spark应用不会立刻计算这些**transformations**的结果，只是记住这些**transformations**操作。只有当应用执行某一个**action**操作并且这个**action**需要某个**transformation**操作的结果时，这个**transformation**才会被计算。这种设计模式使得Spark运行更加高效。

​	默认情况，每个**transformed RDD**在你每次在它上面运行一个**action**时都会被重新计算，显然这是很低效的。对此，Spark提供了持久化的方式，可以让你把**transformation**后的结果RDD保存在内存或者磁盘上，这样如果下次又需要这个transformed RDD的时候就不用再次计算从而可以加快整个计算的速度。

#### 3.1 基本操作-Basic

```java
JavaRDD<String> lines = sc.textFile("data.txt");
JavaRDD<Integer> lineLengths = lines.map(s -> s.length());
int totalLength = lineLengths.reduce((a, b) -> a + b);
```

第一行从一个外部文件创建了一个RDD。这个RDD并不会被加载到内存中，lines只是引用了这个文件而已。第二行的lineLengths是map转换操作的结果，由于**transformation**操作的懒惰性这个map操作的结果并不会马上被计算。最后一行，当执行reduce操作时，由于这是一个**action**，在这个时候，Spark会把这个计算分成多个任务分发到集群中的不同机器上，每个机器就会执行分配给它本地的map和reduce操作，然后返回它的结果值到*driver program*。

如果我们要多次用到**lineLengths**的值,那么我们可以添加下面这一行代码：

```java
lineLengths.persist(StorageLevel.MEMORY_ONLY());
```

在执行reduce操作前，上面这句代码会在lineLengths第一次被计算出来后保存到内存中。

#### 3.2 函数传递-Passing Functions to Spark

​	Spark提供的API对于函数的具有严重的依赖性。在java里面，传递函数只能通过类来展现。有两种方式来创建这样的函数：

- 实现org.apache.spark.api.java.function.Function接口，或者是匿名内部类；
- 在Java 8，使用lambda表达式来简化这个实现。

lanbda表达式的方式上面有样例。下面是通过匿名内部类和实现接口的方式来实现跟上面代码一样的功能：

```java
// 匿名内部类
JavaRDD<String> lines = sc.textFile("data.txt");
JavaRDD<Integer> lineLengths = lines.map(new Function<String, Integer>() {
  public Integer call(String s) { return s.length(); }
});
int totalLength = lineLengths.reduce(new Function2<Integer, Integer, Integer>() {
  public Integer call(Integer a, Integer b) { return a + b; }
});
```

```java
// 实现接口方式
class GetLength implements Function<String, Integer> {
  public Integer call(String s) { return s.length(); }
}
class Sum implements Function2<Integer, Integer, Integer> {
  public Integer call(Integer a, Integer b) { return a + b; }
}

JavaRDD<String> lines = sc.textFile("data.txt");
JavaRDD<Integer> lineLengths = lines.map(new GetLength());
int totalLength = lineLengths.reduce(new Sum());
```

### 4. 理解闭合-Understanding closures

​	在Spark中最难理解的一件事：当在集群中执行代码时，变量和函数的生命周期和作用域的问题。RDD操作在变量的作用域外能够修改他们的值(注意对这一点的理解：是跨机器导致这个问题的出现)是导致这些问题发生的主要原因。

#### 4.1 例子

​	考虑下面的RDD操作，可能在不同的环境下执行会有不同的结果（取决于是否在同一个jvm上运行）。一种常见情况是在Spark的local模式和Spark的cluster模式运行时：

```java
int counter = 0;
JavaRDD<Integer> rdd = sc.parallelize(data);

// Wrong: Don't do this!!
// 在spark中不要这么做
rdd.foreach(x -> counter += x);

println("Counter value: " + counter);
```

**Local vs. cluster mode**

​	上面代码的行为是不确定的。为了执行这个作业，Spark会把RDD操作分配成不同的多个任务进程，每个任务进程都由每个Worker node上的**executor**执行器来执行。在被每个executor执行器执行之前，Spark会计算每个任务的**closure**。这些closure是指这样的变量和方法-为了执行在RDD上的计算必须让executor可见的变量和方法。这些closure会被序列化并被发送到每个executor上面。

​	在closure中的变量现在被发送到了每个executor上，executor中有了这些变量的副本，当**counter**变量在foreach函数中被引用的时候，这个counter变量不再是*driver program*所运行节点上的counter变量了，虽然在*driver program*节点上仍然存在counter这个变量，但是它的变量对所有的**executors**是不可见。executor只能够访问到从closure中复制过来的在本地机器上的counter副本，所以在foreach中对counter的操作都是针对executors本地的counter副本，这些操作结果并不会反应到**driver program**所在节点上。所以，输出counter的最终结果还是零。

​	在local模式，某些条件下，foreach函数将会在一个相同的jvm虚拟机上运行，可能引用的是同一个counter变量，在这种情况下counter的值可能会被更新。

​	在上面的场景中为了确保确定的行为发生，我们应该使用**Accumulator**共享变量。在Spark中Accumulator提供了一种机制来保证在集群中的跨节点并行任务能够安全的更新变量。Accumulator会在稍后讨论。

**Printing elements of an RDD**

​	一种另外的场景是使用rdd.foreach(println)来打印一个RDD中的所有元素。在单机上，这个可以打印出RDD上的元素。然而在集群中，executor的标准输出是写到executor运行机器上的标准输出而不是*driver program*运行节点上的标准输出，所以执行rdd.foreach(println)并不会打印出预想的结果。为了实现打印RDD上的所有元素这个目的，我们可以使用**collect()**方法来将RDD数据带到*driver program*节点上：rdd.collect().foreach(println)。这个操作可能会造成*driver program*节点内存溢出，因为collect()会把RDD的所有数据抓到*driver program*单个节点上。如果你只需要打印少量元素，一种更安全的方式是使用：rdd.take(100).foreach(println)。

### 5. 键值对的RDD-Working with Key-Value Pairs

​	Spark的大多数操作可以在任何类型的RDD上工作，但是有少部分特殊的操作只能运行在key-value形式的RDD上。最常见的一个是“shuffle”操作，比如说：通过键来分组和聚合的操作。

​	key-value形式的RDD通过JavaPairRDD类来表示。我们可以使用mapToPair和flatMapToPair操作来从JavaRDD来构建JavaPairRDD。例如，下面的代码使用reduceByKey操作来计算一个文件中每一行文本出现的次数：

```java
JavaRDD<String> lines = sc.textFile("data.txt");
JavaPairRDD<String, Integer> pairs = lines.mapToPair(s -> new Tuple2(s, 1));
JavaPairRDD<String, Integer> counts = pairs.reduceByKey((a, b) -> a + b);
```

**Shuffle**

​	在Spark中的某些特定操作会触发一个叫做shuffle的事件。Shuffle是spark提供的一种重新分布在不同partitions上数据的机制。显然这个机制涉及了在不同的executors或者machines之间拷贝数据，这使得shuffle成为一个即昂贵又复杂的操作。

​	为了理解什么是**Shuffle操作**，我们以**reduceByKey**操作为例来进行讲解。如我们在附录中所记录的，reduceByKey(function)操作会通过它提供的function函数来计算RDD中所有相同key的聚合结果(如下图)，什么样的结果取决于function的具体实现。

![spark_56](images\spark_56.png)

在进行reduceByKey计算过程中的挑战在于，同一个key是分布在不同的partition甚至不同的机器上的，为了计算得到结果，那么这些分布在不同partition或者不同机器上的同一个key的元素必须重新分配，使他们聚集到同一个partition上，这个过程就称之为**Shuffle**。

​	在Spark中那些包含shuffle操作事件的操作有：repartition、coalesce、ByKey类操作(count操作除外)比如groupByKey，reduceByKey等、join类操作比如cogroup和join。

**Shuffle对执行效率的影响**

​	Shuffle是一个非常昂贵的操作，因为它涉及到了磁盘I/O，数据序列化，网络I/O等。为了重新组织数据进行Shuffle，Spark会生成一个任务集-*map*任务集组织收集数据，*reduce*任务集进行聚合。这里的map和reduce是借鉴MapReduce中的概念，并不是指Spark中的map和reduce算子操作。

​	在Spark里面，每个*map*任务的结果会暂时保存在内存中，最终以文件块方式写入到磁盘上；而*reduce*任务则负责读取跟它相关的文件块。

​	某些Shuffle操作会消耗很多堆内存用于存储*map*或者*reduce*任务组织数据过程中产生的数据结构，当内存不够大时这些数据结构会被写入到磁盘上，这就增加了额外的磁盘I/O和大量的临时中间文件，也会造成大量的磁盘空间的浪费。​

### 6. RDD持久化-RDD Persistance

​	正如上面有提到的，我们可以将一个中间RDD的计算结果保存在内存或者磁盘上。持久化是Spark本身提供的一个重要功能。当我们持久化一个RDD，每个节点会存储属于这个RDD中的partitions，并且这个持久化的RDD能被多个需要它的action重复使用。这个特点使得在以后执行的action能够更加高效。

​	我们可以使用persist()和cache()方法来持久化一个RDD，这个RDD在第一次被计算之后会被保存到节点的内存或者磁盘上。Spark的持久化是可容错的-如果这个持久化RDD的任何partition丢失了，那么Spark会自动重新去计算。

​	此外，每个持久化RDD可以允许你存储为不同的级别。这些存储级别可以由**StorageLevel类**中得到。

| 存储级别                                | 描述                                       |
| ----------------------------------- | ---------------------------------------- |
| MEMORY_ONLY                         | 默认存储级别，将RDD作为java对象存储在jvm内存（不会序列化），如果内存不够，那么多余的partiton就不会被存储，在下次计算时依然会重新计算这些partitions |
| MEMORY_AND_DISK                     | 将RDD存储到jvm内存，如果内存不够，那么就将多余的RDD partitions存到磁盘上，下次需要时从磁盘读取 |
| MEMORY_ONLY_SER(Java and Scala)     | 将RDD作序列化后存到内存中（每个partition是一个字节数组），内存不足时，多余的不会被存储。这种方式更节省内存，尤其当使用快速序列化时，但是因为要序列化和反序列化，会更耗cpu |
| MEMORY_AND_DISK_SER(Java and Scala) | 跟MEMORY_ONLY_SER类似，不过也是把内存不够时多余的partitions存到磁盘，需要时再读取出来 |
| DISK_ONLY                           | 将RDD只存储到磁盘                               |
| MEMORY_ONLY\_2,MEMORY_AND_DISK_2    | 上面的任意一种策略，如果加上后缀_2，表示会在集群的两个节点上保存一份副本数据  |
| OFF_HEAP(experimental)              | 跟MEMORY_ONLY_SER类似，不过是将数据存放在off_heap内存中(可以防止垃圾回收)，这个需要支持off_heap内存功能 |

**数据删除**

​	Spark会自动监控persist或者cache在每个节点的数据并且删除老的数据(使用的LRU least-recently-used算法)。如果要手动删除这些数据，可以调用RDD.unpersist()方法。

### 7. 共享变量-Shared Variables

​	当一个函数被传递给在远程集群节点运行的Spark操作(比如map或者reduce)，函数所用到的变量都是一个独立的副本。这些变量被复制到每个节点，而且在每个节点上的更新不会反馈到*driver program*上。Spark提供两种方式来使用共享变量：**broadcast variables**和**accumulators**。

#### 7.1 广播变量-Broadcast Variables

​	广播变量允许程序员缓存一个**只读变量**在每个机器上，而不是传递副本到每个任务上。他们能被用来以一种有效方式给每个节点传递一个大数据集的拷贝。Spark也通过高效的广播算法来降低广播变量带来的通信消耗。

​	广播变量通过SparkContext.broadcast(v)的方式来创建，广播变量的值可以通过value()方法获得。代码如下：

```java
Broadcast<int[]> broadcastVar = sc.broadcast(new int[] {1, 2, 3});

broadcastVar.value();
// returns [1, 2, 3]
```

​	在一个广播变量被创建以后，应该使用broadcastVar而不要继续使用v来操作。此外，为了确保所有的节点得到相同的广播变量值，v的值在广播之后不应该再被修改。

#### 7.2 Accumulators

​	Accumulators变量只能通过联想和交换操作(associative and commutative operation)来执行added操作。Accumulators变量能够用来实现计数和求和。Spark本身只支持类型为**numeric**的Accumulators变量，我们可以自己增加新的实现类型。

​	如果一个Accumulatos变量被创建，那么它能够在Spark的UI中查看到。

![spark_1](images\spark_1.png)

​	一个Accumulator变量可以通过SparkContext.accumulator(v)的方式来创建。然后每个任务可以通过add方法或者+=(这个操作只在Scala和Python中支持)操作来对他进行操作。但是，每个任务不能都读取Accumulator的值，只有*driver program*能够读取Accumulator变量的值。

下面代码用通过Accumulator变量来计算一个数组中所有元素的和：

```java
LongAccumulator accum = sc.sc().longAccumulator();

sc.parallelize(Arrays.asList(1, 2, 3, 4)).foreach(x -> accum.add(x));
// ...
// 10/09/29 18:41:08 INFO SparkContext: Tasks finished in 0.317106 s

accum.value();
// returns 10
```

​	Accumulator变量原生只支持数值类型，我们可以创建自己的Accumulator变量的数据类型，实现AccumulatorParam接口。例如：

```java
class VectorAccumulatorParam implements AccumulatorParam<Vector> {
  public Vector zero(Vector initialValue) {
    return Vector.zeros(initialValue.size());
  }
  public Vector addInPlace(Vector v1, Vector v2) {
    v1.addInPlace(v2); return v1;
  }
}

// Then, create an Accumulator of this type:
Accumulator<Vector> vecAccum = sc.accumulator(new Vector(...), new VectorAccumulatorParam());
```

### 8. 结语

​	我也是刚刚接触Spark，这篇文章也是基于[官方文档](http://spark.apache.org/docs/latest/programming-guide.html)写的。所以可能有很多细节和概念没有写清楚，但是对于Spark的一个基本理解入门，我觉得是可以的。这篇文章中有什么写的不好和不到位的地方，还请大家多多指出来。

### 附录：常见的transformation和action

**Transformation**

| Transformation                           | Meaning                                  |
| ---------------------------------------- | ---------------------------------------- |
| map(function)                            | 把源RDD中的每一个元素记录传递给function处理，由function返回的每个结果组成结果RDD |
| filter(function)                         | 把源RDD中的每一条记录传递给function处理，function返回为true的记录组成一个新的RDD |
| flatMap(function)                        | 和map算子类似，但是这个算子针对每个输入项可能会返回0到多个结果值，也就是说function的返回值是集合类型的，而map算子中function的返回值是单个对象 |
| mapPartitions(function)                  | 和map算子类似，但这个算子是针对RDD中的每个partition进行计算而不是每个记录了，也就是说function处理类型是Iterator<T>的输入类型，Iterator<U>的输出类型 |
| mapPartitionsWithIndex(function)         | 和mapPartitions算子类似，不过会给function传递一个partition的索引，所以function完成的功能是：(Int, Iterator\<T>)=>Iterator\<U> |
| sample(withReplacement, fraction, seed)  | 取样                                       |
| union(otherDateset)                      | 把源RDD和传入的RDD参数进行合并返回一个新的RDD              |
| intersection(otherDataset)               | 返回源RDD和参数RDD之间的交集                        |
| distinct([numTasks])                     | 返回源RDD中的唯一元素集，去除重复的元素                    |
| groupByKey([numTasks])                   | 在一个(K,V)键值对RDD上时，返回的是(K,Iterator\<V>)格式的键值对结果。 |
| reduceByKey(function,[numTasks])         | 在(K,V)键值对上调用时，返回一个(K,V)键值对结果，其中V是源RDD中相同键的所有值的function结果，function执行的是(V,V)=>V的操作 |
| aggregateByKey(zeroValue)(seqOp,combOp,[numTasks]) | 在(K,V)数据集上调用，返回一个(K,U)结果                 |
| sortByKey([ascending],[numTasks])        | 在(K,V)数据集上调用，其中K必须实现了Ordered接口，返回一个按K排序的(K,V)结果集 |
| join(otherDataset,[numTasks])            | 在(K,V)和(K,W)上进行join操作时，返回(K,(V,W))的结果集   |
| cogroup(otherDataset,[numTasks])         | 在(K,V)和(K,W)上调用时，返回(K,(Iterable\<V>,Iterable\<W>))的结果集，这个操作也被称作groupWith |
| cartesian(otherDataset)                  | 返回两个RDD的笛卡尔积                             |
| pipe(command,[envVars])                  | 把RDD中的每个partition管道到一个shell命令            |
| coalesce(numPartitions)                  | 把RDD的partition数量减少，在一个RDD被filter之后执行这个操作很有用 |
| repartition(numPartition)                | 对一个RDD重新进行shuffle来均衡，会减少或者增加partitions   |
| repartitionAndSortWithinPartitions(partitioner) | 用给定的partitioner重新对RDD进行repartition操作，在每个结果partition中，会对记录进行根据键进行排序。 |

**Action**

| Action                                 | Meaning                                  |
| -------------------------------------- | ---------------------------------------- |
| reduce(function)                       | 使用function函数对数据集中的元素进行聚合                 |
| collect()                              | 将RDD中的所有元素所有数据返回给driver program。常用在filter或者是数据集较小的RDD上 |
| count()                                | 返回RDD中的元素个数                              |
| first()                                | 返回RDD中的第一个元素，相当于take(1)                  |
| take(n)                                | 把RDD中的前n个元素作为数据返回                        |
| takeSample(withReplacement,num,[seed]) |                                          |
| takeOrdered(n,[ordering])              | 返回RDD中的前n个函数，以他们的自然排序或者是自定义比较器           |
| saveAsTextFile(path)                   | 把数据集中的元素写到指定的文本文件或者目录，可以是本地文件系统路径、HDFS或者任何其他Hadoop支持的文件系统。Spark会在RDD中的每个元素上调用toString()方法，把每个元素转换为字符串作为文件的一行写到文件中 |
| saveAsSequenceFile(path)               | 跟saveASTextFile类似，只不过会把每个元素序列化处理，比较适合(K,V)键值对的RDD，并且K,V要实现了Hadoop中的writable接口，spark会自动把Int，Double，String等类型转换 |
| saveAsObjectFile(path)                 | 使用java序列化格式把每个元素写到指定路径，这样保存的文件可以通过SparkContext.objectFile()访问 |
| countByKey()                           | 只适用于(K,V)键值对的RDD，返回一个(K,Int)的hashMap，值是每个key的个数 |
| foreach(function)                      | 在RDD中的每个元素上执行function函数                  |