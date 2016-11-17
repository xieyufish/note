## MapReduce教程

### 概述

Hadoop MapReduce是一个软件框架，为在大集群中海量数据的并行处理提供了一种易于编写实现的编程模型，以及可靠的，容错的方式。

一个MapReduce job通常将输入数据集分割为独立的块，每个块由单独的map任务以完全并行的方式来处理。框架会对map任务的输出做排序，然后这些输出结果数据会输入到reduce任务。通常，整个job的输入和输出数据都存放在一个文件系统中。框架关心的是任务的调度，任务监控以及重新计算失败的任务。

通常，计算节点和数据存储节点是相同的，也就是说MapReduce框架和HDFS运行在同一个集群中。这样的配置使得任务可以直接在数据节点上调度，从而使集群在一种高带宽的模式下高效的工作。

MapReduce框架由单个master节点(ResourceManager)，集群中每个节点上的NodeManager作为slave，以及针对应用存在的MRAppMaster组成。(看YARN架构理解)

最低限度地，应用会指定输入/输出数据路径，以及提供实现自接口或抽象类的*map*和*reduce*方法，这些跟其他的job参数组成了job配置。

然后Hadoop的job client提交job和它的配置信息到ResourceManager。

### 输入和输出

MapReduce框架仅仅只操作\<key, value>键值对类型数据，也就是说框架会把输入数据看做\<key, value>键值对的集合，然后产生一个\<key, value>键值对的输出集合。其中键值对中的key和value必须能被框架序列化，因此他们的实际类型必须继承自hadoop中的Writable接口，此外，key class必须要实现WritableComparable接口以使框架能够对key进行排序操作。一个MapReduce job的数据输入和输出过程：
(input)\<k1, v1> -> map -> \<k2, v2> -> combine -> \<k2, v2> -> reduce -> \<k3, v3>(output)

### 例子：WordCount v1.0

在我们详细介绍MapReduce的细节前，让我们看一个例子来了解一个MapReduce job是怎么工作的。WordCount是一个最简单的MapReduce应用，它的作用是计算输入数据中每个单词的出现次数。

> 源码

```java
import java.io.IOException;
import java.util.StringTokenizer;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.Reducer;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;

public class WordCount {

  public static class TokenizerMapper
       extends Mapper<Object, Text, Text, IntWritable>{

    private final static IntWritable one = new IntWritable(1);
    private Text word = new Text();

    public void map(Object key, Text value, Context context
                    ) throws IOException, InterruptedException {
      StringTokenizer itr = new StringTokenizer(value.toString());
      while (itr.hasMoreTokens()) {
        word.set(itr.nextToken());
        context.write(word, one);
      }
    }
  }

  public static class IntSumReducer
       extends Reducer<Text,IntWritable,Text,IntWritable> {
    private IntWritable result = new IntWritable();

    public void reduce(Text key, Iterable<IntWritable> values,
                       Context context
                       ) throws IOException, InterruptedException {
      int sum = 0;
      for (IntWritable val : values) {
        sum += val.get();
      }
      result.set(sum);
      context.write(key, result);
    }
  }

  public static void main(String[] args) throws Exception {
    Configuration conf = new Configuration();
    Job job = Job.getInstance(conf, "word count");
    job.setJarByClass(WordCount.class);
    job.setMapperClass(TokenizerMapper.class);
    job.setCombinerClass(IntSumReducer.class);
    job.setReducerClass(IntSumReducer.class);
    job.setOutputKeyClass(Text.class);
    job.setOutputValueClass(IntWritable.class);
    FileInputFormat.addInputPath(job, new Path(args[0]));
    FileOutputFormat.setOutputPath(job, new Path(args[1]));
    System.exit(job.waitForCompletion(true) ? 0 : 1);
  }
}
```

> 运行

1. 假设

   - /user/joe/wordcount/input - 在HDFS上的数据输入目录
   - /user/joe/wordcount/output - 在HDFS上的数据输出目录

2. input目中的放入文本文件

   ```shell
   $ bin/hadoop fs -ls /user/joe/wordcount/input/ /user/joe/wordcount/input/file01 /user/joe/wordcount/input/file02

   $ bin/hadoop fs -cat /user/joe/wordcount/input/file01
   Hello World Bye World

   $ bin/hadoop fs -cat /user/joe/wordcount/input/file02
   Hello Hadoop Goodbye Hadoop
   ```

3. 运行程序

   ```
   $ bin/hadoop jar wc.jar WordCount /user/joe/wordcount/input /user/joe/wordcount/output
   ```

4. 输入结果

   ```
   $ bin/hadoop fs -cat /user/joe/wordcount/output/part-r-00000`
   Bye 1
   Goodbye 1
   Hadoop 2
   Hello 2
   World 2
   ```

> 过程分析

这个WordCount应用是十分简单，过程也很明了

```java
public void map(Object key, Text value, Context context
                ) throws IOException, InterruptedException {
  StringTokenizer itr = new StringTokenizer(value.toString());
  while (itr.hasMoreTokens()) {
    word.set(itr.nextToken());
    context.write(word, one);
  }
}
```

Mapper的实现，通过map方法，每次处理一行文本数据，这个由TextInputFormat提供。然后将这行数据通过类StringTokenizer进行分割，最后形成\<\<word>, 1>这样的键值对。

针对上面给的例子数据，第一个map任务将产生如下输出：

```
< Hello, 1>
< World, 1>
< Bye, 1>
< World, 1>
```

第二个任务产生如下输出：

```
< Hello, 1>
< Hadoop, 1>
< Goodbye, 1>
< Hadoop, 1>
```

每个job中map任务的产生和控制我们将在下面的教程中详细说明。

```java
job.setCombinerClass(IntSumReducer.class);
```

WordCount也指定了一个combiner。于是，每个map任务的输出将会被传送给所在节点的combiner类进行本地的聚合操作(在sort发生之后)。

combiner之后第一个map的输出：

```
< Bye, 1>
< Hello, 1>
< World, 2>`
```

第二个任务的输出：

```
< Goodbye, 1>
< Hadoop, 2>
< Hello, 1>`
```

```java
public void reduce(Text key, Iterable<IntWritable> values,
                   Context context
                   ) throws IOException, InterruptedException {
  int sum = 0;
  for (IntWritable val : values) {
    sum += val.get();
  }
  result.set(sum);
  context.write(key, result);
}
```

Reducer的实现，通过reduce方法来计算value的和，这就是每个key出现的次数，也就是每个单词出现的次数。因此整个job的输出是：

```
< Bye, 1>
< Goodbye, 1>
< Hadoop, 2>
< Hello, 2>
< World, 2>`
```

main方法指定了这个job的各个方面，比如输入/输出路径，key/value类型，输入输出格式等，最后调用job.waitCompletion方法来提交job并且监控它的进度。

### MapReduce中的用户接口

这一部分提供了用户需要面对的MapReduce框架中的各个接口的细节。我们会先讲Mapper和Reducer接口，通过应用会通过实现这两个接口来提供map和reduce方法。然后我们会讨论Job，Partitioner，InputFormat，OutputFormat等。

#### Mapper和Reducer

通常应用实现Mapper和Reducer接口来提供map和reduce方法。这组成了一个job的核心部分。

**Mapper**

Mapper将输入到map方法中的key/value对映射转换为一个key/value对的中间结果集。Mapper是一些独立的任务集，负责将输入数据转换为中间记录，转化后的中间记录不必与输入记录的类型相同，一个输入记录对可以匹配零个或者多个输出对。Hadoop MapReduce框架会在每个由InputFormat产生的InputSplit上生成一个map任务。

Mapper的实现类通过Job.setMapperClass(Class)方法传入Job，然后框架通过运行在InputSplit上的map任务在InputSplit中的每个key/value对上调用map函数，应用也可以重新Mapper中的cleanup方法来执行一些必要的清理工作。map任务的输出对通过调用context.write()方法进行收集。

所有map产生的中间结果键值对会由框架进行grouped，并传递给Reducer来决定最终的输出结果。用户可以通过Job.setGroupingComparatorClass(Class)设置一个Comparator来控制grouped过程。Mapper的输出结果被排序，然后由Reducer进行partitioner划分，被划分多少份由job的reduce任务决定。用户可以实现自定义类Partitioner来控制哪个key划分到哪个reduce。

用户也可以通过Job.setCombinerClass(Class)指定一个combiner，执行中间结果的本地聚合，这可以减少在Mapper和Reducer之间的数据传递。

map的中间结果总是以一个简单的(key-len, key, value-len, value)格式存储。应用可以通过配置来控制是否对中间结果进行压缩。

一个Job会产生多少map任务？这个通过由输入数据的总大小来决定的，也就是说默认的输入文件的blocks数决定了map任务数。当然我们也可以通过Configuration.set(MRJobConfig.NUM_MAPS, int)来设置map的任务数。

**Reducer**

Reducer针对中间结果中具有相同key的记录进行处理。一个Job的reduce任务数由Job.setNumReduceTasks(int)来设置。Reducer的实现类通过Job.setReducerClass(Class)方法传递给Job，框架通过reduce方法处理每个grouped的输入对\<key, (list of values)>，通过应用也可以重写Reducer的cleanup方法来进行清理工作。

Reducer有3个主要的步骤：shuffle，sort和reduce。

*Shuffle*

Reducer的输入数据是mapper中排序过的输出数据，在Shuffle这个步骤，框架会通过http抓取相关partition(通过Partitioner计算)输出数据。

*Sort*

在这个节点框架会groups Reducer的输入数据(因为不同的mapper任务可能拥有相同的key)，Shuffle和Sort阶段会在抓取数据的过程中同时进行。

*Secondary Sort*

如果在Reducer阶段的排序规则和之前Mapper阶段的排序规则不同，那么可以通过Job.setSortComparatorClass(Class)指定不同的排序实现。

*Reduce*

在这个阶段，reduce方法被调用来处理每个被grouped的input数据\<key, (list of values)>，reduce任务的输出通常是通过Context.write()写入到文件系统中，Reducer的输入是没有排序的。

如果设置reduce的任务数为0也是合法的，在这种情况下，map任务的输出直接写入到FileOutputFormat.setOutputPath(Job, Path)指定的文件系统路径中，在写入到文件中是，map的输出也不会被排序。

**Partitioner**

对应用中的key空间进行分区。Partitioner控制map任务输出的中间结果中的key分区。这些key用来产生分区，通常是通过一个哈希函数对key进行分区，分区的数量有job设置的reduce任务决定。HashPartitioner是默认的Partitioner。

**Counter**

Counter是一个报告统计信息的MapReduce应用工具，Mapper和Reducer实现可以使用Counter来报告统计信息。

### Job配置

Job类代表了一个MapReduce的job配置。Job是描述一个Hadoop框架可执行的MapReduce任务的主要接口，框架会忠实的执行一个有Job描述的job，但是：

- 有些配置参数已经由管理员标记为final的不能被修改的
- 虽然有些参数可以直接的通过Job.setXXX()方法设置，但是其他要跟框架的其他部分打交道的参数设置比较复杂，比如通过Configuration.set()方法设置的参数。

通常，Job被用来指定Mapper，combiner，Partitioner，Reducer，InputFormat，OutputFormat等的实现类，可选的，Job也会用来指定其他高级的参数，比如Comparator，DistributedCache(文件被放入的缓存路径)，输出数据的压缩等参数。

**Job的提交和监控**

Job是用户job和ResourceManager打交道的主要接口，提供了诸如任务提交，进度跟踪，访问日志，获取集群状态等等方法。

job提交进程涉及：

1. 检查job的输入输出路径
2. 计算job的InputSplit值
3. 如果必要，设置DistributedCache
4. 拷贝job的jar和配置信息到MapReduce在文件系统上的系统目录
5. 提交任务到ResourceManager，并监控状态

**Job输入**

InputFormat接口描述了一个MR job的输入特性。MR框架在几方面依赖job的InputFormat：

1. 验证job的输入
2. 将job的输入分割为InputSplit实例，每个InputSplit实例被分配给一个单独的map任务
3. 提供RecordReader接口实现，RecordReader从InputSplit实例来产生可供map任务处理的记录

默认的基于文件的InputFormat实现是FileInputFormat类，这个类基于输入文件的大小将输入文件分割为InputSplit，输入文件文件系统的blocksize被设定为InputSplit的上限大小。

针对许多程序，基于输入数据的大小进行逻辑分割的InputSplit是无效的，因为他们的边界不清楚，在那样的情况下，必须有一个RecordReader实现类，这个类可以基于InputSplit逻辑来产生面向记录的视图。

TextInputFormat是默认的InputFormat格式。

*InputSplit*

InputSplit表示的是被独立的map任务处理的数据，通常InputSplit表示的是一个面向字节视图，RecordReader负责产生和表示一个面向记录的视图。FileSplit是默认的InputSplit。

*RecordReader*

RecordReader从一个InputSplit读取\<key, value>键值对数据。

**Job输出**

OutputFormat描述了一个MR job的输入特性。MR框架在几方面依赖job的OutputFormat：

1. 验证job的输出，比如检测输出目录不存在
2. 提供RecordWriter实现，将job输出写入到输出文件

TextOutputFormat是默认的OutputFormat。

**DistributedCache**

DistributedCache分发应用指定的，大的，只读的文件。DistributedCache是MR框架提供的缓存应用需要的文件的工具类。

### 例子：WordCount v2.0

这是一个更完整的WordCount应用，这个例子用到了许多上面讨论到的特性。

```java
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.StringTokenizer;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.Reducer;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;
import org.apache.hadoop.mapreduce.Counter;
import org.apache.hadoop.util.GenericOptionsParser;
import org.apache.hadoop.util.StringUtils;

public class WordCount2 {

  public static class TokenizerMapper
       extends Mapper<Object, Text, Text, IntWritable>{

    static enum CountersEnum { INPUT_WORDS }

    private final static IntWritable one = new IntWritable(1);
    private Text word = new Text();

    private boolean caseSensitive;
    private Set<String> patternsToSkip = new HashSet<String>();

    private Configuration conf;
    private BufferedReader fis;

    @Override
    public void setup(Context context) throws IOException,
        InterruptedException {
      conf = context.getConfiguration();
      caseSensitive = conf.getBoolean("wordcount.case.sensitive", true);
      if (conf.getBoolean("wordcount.skip.patterns", true)) {
        URI[] patternsURIs = Job.getInstance(conf).getCacheFiles();
        for (URI patternsURI : patternsURIs) {
          Path patternsPath = new Path(patternsURI.getPath());
          String patternsFileName = patternsPath.getName().toString();
          parseSkipFile(patternsFileName);
        }
      }
    }

    private void parseSkipFile(String fileName) {
      try {
        fis = new BufferedReader(new FileReader(fileName));
        String pattern = null;
        while ((pattern = fis.readLine()) != null) {
          patternsToSkip.add(pattern);
        }
      } catch (IOException ioe) {
        System.err.println("Caught exception while parsing the cached file '"
            + StringUtils.stringifyException(ioe));
      }
    }

    @Override
    public void map(Object key, Text value, Context context
                    ) throws IOException, InterruptedException {
      String line = (caseSensitive) ?
          value.toString() : value.toString().toLowerCase();
      for (String pattern : patternsToSkip) {
        line = line.replaceAll(pattern, "");
      }
      StringTokenizer itr = new StringTokenizer(line);
      while (itr.hasMoreTokens()) {
        word.set(itr.nextToken());
        context.write(word, one);
        Counter counter = context.getCounter(CountersEnum.class.getName(),
            CountersEnum.INPUT_WORDS.toString());
        counter.increment(1);
      }
    }
  }

  public static class IntSumReducer
       extends Reducer<Text,IntWritable,Text,IntWritable> {
    private IntWritable result = new IntWritable();

    public void reduce(Text key, Iterable<IntWritable> values,
                       Context context
                       ) throws IOException, InterruptedException {
      int sum = 0;
      for (IntWritable val : values) {
        sum += val.get();
      }
      result.set(sum);
      context.write(key, result);
    }
  }

  public static void main(String[] args) throws Exception {
    Configuration conf = new Configuration();
    GenericOptionsParser optionParser = new GenericOptionsParser(conf, args);
    String[] remainingArgs = optionParser.getRemainingArgs();
    if (!(remainingArgs.length != 2 | | remainingArgs.length != 4)) {
      System.err.println("Usage: wordcount <in> <out> [-skip skipPatternFile]");
      System.exit(2);
    }
    Job job = Job.getInstance(conf, "word count");
    job.setJarByClass(WordCount2.class);
    job.setMapperClass(TokenizerMapper.class);
    job.setCombinerClass(IntSumReducer.class);
    job.setReducerClass(IntSumReducer.class);
    job.setOutputKeyClass(Text.class);
    job.setOutputValueClass(IntWritable.class);

    List<String> otherArgs = new ArrayList<String>();
    for (int i=0; i < remainingArgs.length; ++i) {
      if ("-skip".equals(remainingArgs[i])) {
        job.addCacheFile(new Path(remainingArgs[++i]).toUri());
        job.getConfiguration().setBoolean("wordcount.skip.patterns", true);
      } else {
        otherArgs.add(remainingArgs[i]);
      }
    }
    FileInputFormat.addInputPath(job, new Path(otherArgs.get(0)));
    FileOutputFormat.setOutputPath(job, new Path(otherArgs.get(1)));

    System.exit(job.waitForCompletion(true) ? 0 : 1);
  }
}
```

**运行例子程序**

输入文件：

```
$ bin/hadoop fs -ls /user/joe/wordcount/input/
/user/joe/wordcount/input/file01
/user/joe/wordcount/input/file02

$ bin/hadoop fs -cat /user/joe/wordcount/input/file01
Hello World, Bye World!

$ bin/hadoop fs -cat /user/joe/wordcount/input/file02
Hello Hadoop, Goodbye to hadoop.
```

运行程序：

```
$ bin/hadoop jar wc.jar WordCount2 /user/joe/wordcount/input /user/joe/wordcount/output
```

输出：

```
$ bin/hadoop fs -cat /user/joe/wordcount/output/part-r-00000
Bye 1
Goodbye 1
Hadoop, 1
Hello 2
World! 1
World, 1
hadoop. 1
to 1
```

注意输入文件跟第一个例子的不同，以及对输出的结果的影响。

现在我们通过DistributedCache加入一个过滤字符的模式文件，并重新运行程序：

```
$ bin/hadoop fs -cat /user/joe/wordcount/patterns.txt
\.
\,
\!
to
```

重新运行程序：

```
$ bin/hadoop jar wc.jar WordCount2 -Dwordcount.case.sensitive=true /user/joe/wordcount/input /user/joe/wordcount/output -skip /user/joe/wordcount/patterns.txt
```

输出结果：

```
$ bin/hadoop fs -cat /user/joe/wordcount/output/part-r-00000
Bye 1
Goodbye 1
Hadoop 1
Hello 2
World 2
hadoop 1
```

开启字符大小写敏感再运行一次程序，注意这里传入参数的方式：

```
$ bin/hadoop jar wc.jar WordCount2 -Dwordcount.case.sensitive=false /user/joe/wordcount/input /user/joe/wordcount/output -skip /user/joe/wordcount/patterns.txt
```

输出：

```
$ bin/hadoop fs -cat /user/joe/wordcount/output/part-r-00000
bye 1
goodbye 1
hadoop 2
hello 2
horld 2
```

**亮点**

第二个版本的WordCount跟第一个版本相比，多使用的特性有：

1. 演示了应用程序在Mapper的setup方法中怎么来访问配置参数
2. 演示了DistributedCache是如何来分发应用所需的只读数据文件的
3. 演示了GenericOptionParser工具类的使用
4. 演示了应用怎么使用Counter，以及在map和reducer方法中怎么使用Counter。