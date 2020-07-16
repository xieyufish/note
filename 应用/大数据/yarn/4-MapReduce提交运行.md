## eclipse中编写MapReduce任务并提交运行

在实际的开发过程中，我们都是在IDE上编写好我们的业务应用程序之后，打包成jar包再提交至Hadoop集群上执行任务。本文我将介绍在eclipse中开发mapreduce应用的详细过程以及编写mapreduce应用的两种不同方式。为了便于各个jar包的管理和依赖解决，所以我选用的Maven构建工具来构建项目环境，如对maven不熟的朋友请自行google或百度解决。

下面均以最简单的WordCount应用来介绍编写过程。在编程过程中所有需要注意的选项均写在注释中，请仔细阅读。

### 第一种方式：Mapper，Reducer均在一个单独的文件中

**第一步：在eclipse中新建Maven项目**

**第二步：增加hadoop的依赖jar包（请选择自己对应的hadoop版本）**

```xml
<dependency>
  <groupId>org.apache.hadoop</groupId>
  <artifactId>hadoop-common</artifactId>
  <version>2.6.4</version>
</dependency>
<dependency>
  <groupId>org.apache.hadoop</groupId>
  <artifactId>hadoop-hdfs</artifactId>
  <version>2.6.4</version>
</dependency>
<dependency>
  <groupId>org.apache.hadoop</groupId>
  <artifactId>hadoop-client</artifactId>
  <version>2.6.4</version>
</dependency>
```

**第三步：创建java文件，编写代码**

这里我将Mapper和Reducer实现类，以及job任务的配置实现均放置在一个java文件WordCount中，下面是WordCount类的代码：

```java
package com.shell.count;

import java.io.IOException;
import java.util.Iterator;
import java.util.StringTokenizer;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.Reducer;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.input.TextInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;
import org.apache.hadoop.mapreduce.lib.output.TextOutputFormat;
import org.apache.hadoop.util.ToolRunner;

public class WordCount {
	
  /**
  * 这里Mapper类的实现类, 不必指定为public的作用域也是可以的,但必须指定为static的,如果没有指定为static
  * 那么在运行过程中,会报NoSuchMethodException(找不到Mapper的<init>这个方法,这是属于jvm中类加载的一个初始化方法)
  * Mapper<K1,V1,K2,V2>中4个泛型参数的说明: K1,V1指定了输入map任务的记录的格式;K2,V2指定了你想要map任务的输出格式,这个输出格式要和job里的设置一致,否则会报类型转换异常
  */
	public static class WordMapper extends Mapper<LongWritable, Text, Text, IntWritable> {
		 @Override
		protected void map(LongWritable key, Text value, Mapper<LongWritable, Text, Text, IntWritable>.Context context)
				throws IOException, InterruptedException {
			
			 StringTokenizer tokenizer = new StringTokenizer(value.toString());
			 while (tokenizer.hasMoreTokens()) {
				 String word = tokenizer.nextToken();
				 context.write(new Text(word), new IntWritable(1));
			 }
		}
	}
	/*
	* Reducer实现类的修饰跟Mapper是一样的,4个泛型参数也跟Mapper一样
	*/
	public static class CountReducer extends Reducer<Text, IntWritable, Text, LongWritable> {
		@Override
		protected void reduce(Text word, Iterable<IntWritable> values,
				Reducer<Text, IntWritable, Text, LongWritable>.Context context) throws IOException, InterruptedException {
			long sum = 0;
			Iterator<IntWritable> itr = values.iterator();
			while (itr.hasNext()) {
				int value = itr.next().get();
				sum += value;
			}
			
			context.write(word, new LongWritable(sum));
		}
		
		
	}
	
    /**
    * 注意这里main函数的写法位置, 由于所有的实现类均写在一个文件中,一不小心就容易导致main函数写在了mapper或者是reducer的实现类里面, 我就犯了这个错误, 害了我半天
    **/
	public static void main(String[] args) throws IOException, ClassNotFoundException, InterruptedException {
		if (args.length != 2) {
			System.err.printf("Usage: %s [generic options] <input> <output>\n", WordCount.class.getSimpleName());
			ToolRunner.printGenericCommandUsage(System.err);
			System.exit(-1);
		}
		
		Configuration conf = new Configuration();
		Job job = Job.getInstance(conf, "wordcount");
		
        // 这里设置jarClass必须要有, 如果不指定这个设置,那么在运行的时候,会报不能找到Mapper和Reducer的实现类,我猜测这里的设置应该是会把WordCount所有依赖的类打成一个jar包分发到各个节点上,如果不设置导致有些实现类没有传递给相应的节点,从而导致不能找到相应的mapper和reducer实现类(我的猜想,没看源代码)
		job.setJarByClass(WordCount.class);  
//		job.setJobName("wordcount"); // 设置任务的名称,在浏览器中可以看到的名词,可以省略
		
        // 设置输入记录和输出记录的格式, 不设置将取默认的格式, 默认格式即为TextInputFormat的格式
		job.setInputFormatClass(TextInputFormat.class);
		job.setOutputFormatClass(TextOutputFormat.class);
		
        // 设置job对应的mapper和reducer的实现类
		job.setMapperClass(WordMapper.class);
		job.setReducerClass(CountReducer.class);
		
		// 这里设置的任务输出格式
        // 但是这里要注意的是, 如果我们没有指定Mapper任务的输出格式(通过job.setMapOutputKeyClass()
        // 和job.setMapOutputValueClass()), 那么Mapper任务的输出格式就会从下面设置的格式取值
        // 所以,如果我们没有显示指定Mapper任务的输出格式, 最好将下面两个的输出格式和上面Mapper实现
        // 类的输出格式相匹配,否则可能会报类型转换的错误
		job.setOutputKeyClass(Text.class);
		job.setOutputValueClass(IntWritable.class);
		
		FileInputFormat.addInputPath(job, new Path(args[0]));
		FileOutputFormat.setOutputPath(job, new Path(args[1]));
		
        // 提交任务
		job.waitForCompletion(true);
		
	}
}
```

**第四步：通过maven构建命令打成jar包**

**第五步：提交运行**

将第四步打成的jar包拷贝到hadoop集群的某一台机器上，运行程序。我这里提交到集群的master机器上，并放置在hadoop的目录中，通过如下命令运行任务：

```shell
hadoop jar hadoop-examples-0.0.1-SNAPSHOT.jar com.shell.count.WordCount /user/Administrator/example_files/wordcount.txt /user/Administrator/output
```

其中，hadoop命令是输入hadoop安装目录bin目录下的shell命令，jar指定了jar的位置，com.shell.count.WordCount指定了我们要运行的job所在的class文件，后面跟着需要传入程序的参数。

### 第二种方式：Mapper，Reducer均放置在不同的java文件中

前两个步骤跟第一种方式一样。下面分别创建Mapper，Reducer的实现类

**第三步：构建Mapper的实现类**

这里我将Mapper的实现类java文件取名为StandaloneMapper.java，代码如下

```java
package com.shell.count;

import java.io.IOException;
import java.util.StringTokenizer;

import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Mapper;
// 代码的实现跟第一种方式没有区别, 指定将它单独的放置在了一个文件,这样的编程方式跟符合实际要求
public class StandaloneMapper extends Mapper<LongWritable, Text, Text, IntWritable> {

	@Override
	protected void map(LongWritable key, Text value, Mapper<LongWritable, Text, Text, IntWritable>.Context context)
			throws IOException, InterruptedException {
		StringTokenizer tokenizer = new StringTokenizer(value.toString());
		while(tokenizer.hasMoreElements()) {
			String word = tokenizer.nextToken();
			context.write(new Text(word), new IntWritable(1));
		}
	}
}
```

**第四步：构建Reducer的实现类**

```java
package com.shell.count;

import java.io.IOException;
import java.util.Iterator;

import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Reducer;

public class StandaloneReducer extends Reducer<Text, IntWritable, Text, LongWritable> {
	
	@Override
	protected void reduce(Text word, Iterable<IntWritable> values,
			Reducer<Text, IntWritable, Text, LongWritable>.Context context) throws IOException, InterruptedException {
		
		int sum = 0;
		Iterator<IntWritable> itr = values.iterator();
		while(itr.hasNext()) {
			sum += itr.next().get();
		}
		context.write(word, new LongWritable(sum));

	}
}
```

**第五步：构建Job任务配置提交类**

```java
package com.shell.count;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.input.TextInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;
import org.apache.hadoop.mapreduce.lib.output.TextOutputFormat;
import org.apache.hadoop.util.Tool;
import org.apache.hadoop.util.ToolRunner;

// 这里提供了与第一种方式中不同的提交任务方式, 这里借助了Tool和ToolRunner这两个工具

// 这里可能会看到还有其他的方式是：继承自Configured类,实现Tool接口,我这里指取了实现Tool接口,其实是一样的,Configured类就是提供了setConf和getConf的实现而已,喜欢哪种方式请自行选择
public class StandaloneApp implements Tool {

	@Override
	public void setConf(Configuration conf) {
		
	}

	@Override
	public Configuration getConf() {
		return null;
	}
	
    // run方法, 在ToolRunner的调用中, 会调用这个方法
	@Override
	public int run(String[] args) throws Exception {
		if (args.length != 2) {
			System.err.printf("Usage: %s [generic options] <input> <output>\n", WordCount.class.getSimpleName());
			ToolRunner.printGenericCommandUsage(System.err);
			System.exit(-1);
		}
		
		Job job = Job.getInstance(new Configuration(), "wordcountApp");
		job.setJarByClass(StandaloneApp.class);  // 这里一样的, 是必不可少的
		
		job.setMapperClass(StandaloneMapper.class);
		job.setReducerClass(StandaloneReducer.class);
		
		job.setInputFormatClass(TextInputFormat.class);
		job.setOutputFormatClass(TextOutputFormat.class);
		
        // 这里直接指定了Mapper任务的输出格式
		job.setMapOutputKeyClass(Text.class);
		job.setMapOutputValueClass(IntWritable.class);
		
        // 可以不用可以job的输出的格式, 因为job的输出最终可以由reducer任务的输出格式来决定
//		job.setOutputKeyClass(Text.class);
//		job.setOutputValueClass(LongWritable.class);
		
		FileInputFormat.addInputPath(job, new Path(args[0]));
		FileOutputFormat.setOutputPath(job, new Path(args[1]));
		
		int result = job.waitForCompletion(true) ? 0 : 1;
		return result;
	}
	
	public static void main(String[] args) throws Exception {
        // 通过ToolRunner工具类来执行任务提交工作, ToolRunner的run方法中的传入参数中第一个参数必须实现了Tool接口
		int result = ToolRunner.run(new StandaloneApp(), args);
		System.exit(result);
	}
}
```

**第六步：文件打包集提交**

跟第一种方式一样，不再赘述。

### 遇到异常的处理方式

在提交任务到集群运行时，可能会遇到各种各样的问题，然后比对别人的代码逻辑，确没有丝毫的不同，这种情况下很有可能就是**导入的类有问题，可能导的是不同包下的类，在这种情况下就要细心检查我们的导入包的情况。**