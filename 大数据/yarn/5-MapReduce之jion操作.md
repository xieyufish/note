## MapReduce实现join

在我们平常的大数据项目开发和项目需求中，可能需要我们完成在关系型数据库中十分常见的join类功能。那么针对这种类型的功能需求，用hadoop中的MapReduce模型应该要怎么实现呢？本篇文章将针对这种功能需求提供几种实现选择。

首先，我的开发环境为：**jdk1.7**，**hadoop2.6.4**，**CentOS7**

### 1. 利用DistributedCache实现Join

**DistributedCache：**这是Hadoop自带的一个缓存文件功能，通过这个功能Hadoop可以将用户指定的整个文件拷贝分发到Job所运行的所有节点上，在各个节点上可以通过特定的接口访问读取这个缓存的文件。

在Hadoop中，join功能的实现可以发生在map端，也可以在reduce端实现，下面将分在map端和reduce端实现join来讲解如何通过DistributedCache来实现Join。

#### 1.1 实现map端join

**场景：**我们以部门、员工场景为例，部门和员工信息分别存放在不同的两个文件中，文件格式分别如下：

员工文件内容如下：
\#员工号	员工生日	      firstname  lastname  性别    入职日期        所属部门号
10001	1953-09-02	Georgi	Facello	M	1986-06-26	d005
10002	1964-06-02	Bezalel	Simmel	F	1985-11-21	d007
10003	1959-12-03	Parto	Bamford	M	1986-08-28	d004
10004	1954-05-01	Chirstian	Koblick	M	1986-12-01	d004
10005	1955-01-21	Kyoichi	Maliniak	M	1989-09-12	d003
10006	1953-04-20	Anneke	Preusig	F	1989-06-02	d005
10009	1952-04-19	Sumant	Peac	F	1985-02-18	d006

部门文件内容如下：
\#部门号   部门名称
d001	Marketing
d002	Finance
d003	Human Resources
d004	Production
d005	Development
d006	Quality Management
d007	Sales
d008	Research
d009	Customer Service

**需要完成的功能：**输出员工信息以及其所在部门的部门名称。

**分析：**现在我们有两个文件需要输入到MapReduce中，去进行Join操作，并且不打算用多个Mapper实现类来分别处理这两个文本文件，那么在这种情况下，我们就可以使用DistributedCache这个功能，那么我们应该将哪个文件缓存呢？小的那个，因为DistributedCache是将要整个文件拷贝复制到各个节点上的，太大占用的内存空间和网络传输的时间都将增大，所以建议将比较小的文件作为DistributedCache缓存文件。我这里是做测试，用到的文件都是很小的文件，我这里指定部门文件作为缓存文件。(如果要进行join的文件都很大，那么不建议使用DistributedCache功能实现join，可以选择实现多个Mapper类来完成这个功能，这个下面将会讲到)。那就具体的代码实现以及注意的地方有哪些呢？下面将在代码中指出。

*Driver.java-mapreduce主程序类*

```java
package com.shell.join.mapsidejoin;

import org.apache.hadoop.conf.Configured;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.input.TextInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;
import org.apache.hadoop.mapreduce.lib.output.TextOutputFormat;
import org.apache.hadoop.util.Tool;
import org.apache.hadoop.util.ToolRunner;

import com.shell.count.WordCount;

public class Driver extends Configured implements Tool {

	@Override
	public int run(String[] args) throws Exception {
        // 输入3个参数,分别指定:输入文件,输出文件目录,以及要缓存的文件
		if (args.length != 3) {
			System.err.printf("Usage: %s [generic options] <input> <output> <cachefile>\n", WordCount.class.getSimpleName());
			ToolRunner.printGenericCommandUsage(System.err);
			System.exit(-1);
		}
		
		Job job = Job.getInstance();
		job.setJarByClass(getClass());
		job.setJobName("MapperSideJoin");
		
		job.setInputFormatClass(TextInputFormat.class);
		FileInputFormat.addInputPath(job, new Path(args[0]));
		
		job.setMapperClass(MapperSideJoinDCacheTextFile.class);
		job.setOutputKeyClass(Text.class);
		job.setOutputValueClass(Text.class);
		
        // 这里reduce的任务数设置为0, 表示map任务完成之后,不再进行reduce将直接结束job
        // 根据具体的业务设置reduce任务数
		job.setNumReduceTasks(0);
		
		job.setOutputFormatClass(TextOutputFormat.class);
		FileOutputFormat.setOutputPath(job, new Path(args[1]));
		
        // 上面的配置跟一般的Job配置一样的,没啥区别
        // 这里是关键,这里指定了要DistributedCache缓存的文件的位置(注意这个文件默认是hdfs协议访问,
        // 所以建议放置在HDFS中),设置好这个文件之后,在mapper或者reduce端就可以通过特定接口来访问
		job.addCacheFile(new Path(args[2]).toUri());
		
		return job.waitForCompletion(true) ? 0 : 1;
	}
	
	public static void main(String[] args) throws Exception {
		System.exit(ToolRunner.run(new Driver(), args));
	}

}
```

*MapperSileJoinDCacheTextFile.java-mapper实现类*

```java
package com.shell.join.mapsidejoin;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;

import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.Mapper;

public class MapperSideJoinDCacheTextFile extends Mapper<LongWritable, Text, Text, Text> {
	private HashMap<String, String> departmentMap = new HashMap<>();
    // MapReduce中的Counter,这些设置的Counter根据使用情况将在任务执行完之后
    // 在控制台中打印出来
    // 根据需要配置
	private enum MYCOUNTER {
		RECORD_COUNT,
		FILE_EXISTS,
		FILE_NOT_FOUND,
		SOME_OTHER_ERROR
	}
	
	@Override
	protected void setup(Mapper<LongWritable, Text, Text, Text>.Context context)
			throws IOException, InterruptedException {
        // 通过Job提供的接口方法,得到所有DistributedCache文件在本地节点的存放路径
        // 从这一点也可以知道DistributedCache文件时放置在磁盘中,而不是内存里面的
        // 根据这个路径,就可以以本地文件访问的方式读取这个DistributedCache的文件
		Path[] cacheFiles = Job.getInstance(context.getConfiguration()).getLocalCacheFiles();
		for (Path cacheFile : cacheFiles) {
			System.out.println(cacheFile.toString());
            // 针对需要的缓存文件进行处理
			if (cacheFile.getName().toString().trim().equals("departments.txt")) {
				context.getCounter(MYCOUNTER.FILE_EXISTS).increment(1); // Counter的运用
				loadDepartmentsHashMap(cacheFile, context);
			}
		}
	}
	// 将指定路径的文件内容读取到map中
	private void loadDepartmentsHashMap(Path path, Context context) {
		BufferedReader bufferedReader = null;
		try {
			bufferedReader = new BufferedReader(new FileReader(path.toString()));
			String line = null;
			while((line = bufferedReader.readLine()) != null) {
				System.out.println(line);
				String[] departmentArray = line.split("\t");
				System.out.println(Arrays.toString(departmentArray));
				departmentMap.put(departmentArray[0].trim(), departmentArray[1].trim());
			}
			
		} catch (FileNotFoundException e) {
			e.printStackTrace();
			context.getCounter(MYCOUNTER.FILE_NOT_FOUND).increment(1);
		} catch (IOException e) {
			e.printStackTrace();
			context.getCounter(MYCOUNTER.SOME_OTHER_ERROR).increment(1);
		} finally {
			if (bufferedReader != null) {
				try {
					bufferedReader.close();
				} catch (IOException e) {
					
				} finally {
					bufferedReader = null;
				}
			}
		}
	}
	
    // 在map中完成join操作
	@Override
	protected void map(LongWritable key, Text value, Mapper<LongWritable, Text, Text, Text>.Context context)
			throws IOException, InterruptedException {
		
		context.getCounter(MYCOUNTER.RECORD_COUNT).increment(1);
		
		String employee = value.toString();
		String[] employeeArray = employee.split("\t");
		
		String deptId = employeeArray[6];
		System.out.println(departmentMap);
		String deptName = departmentMap.get(deptId);
		
		Text outputKey = new Text(employeeArray[0]);
		Text outputValue = new Text(employeeArray[1] + "\t"
				+ employeeArray[2] + "\t"
				+ employeeArray[3] + "\t"
				+ employeeArray[4] + "\t"
				+ employeeArray[5] + "\t"
				+ employeeArray[6] + "\t"
				+ deptName);
		context.write(outputKey, outputValue);
	}

}
```

#### 1.2 实现Reduce端join

**场景：**现在有三个文件，三个文件分别存放内容如下。

*UserDetails.txt*
\#用户操作号(唯一)  用户名
9901972231,RevanthReddy
9986570643,Kumar
9980873232,Anjali
9964472219,Anusha
9980874545,Ravi
9664433221,Geetha
08563276311,Kesava
0863123456,Jim
080456123,Tom
040789123,Harry
020789456,Richa

*DeliveryDetails.txt*
\#操作号         操作结果码
9901972231,001
9986570643,002
9980873232,003
9964472219,004
9980874545,001
9664433221,002
08563276311,003
0863123456,001
080456123,001
040789123,001
020789456,001

*DeliveryStatusCodes.txt*
\#结果码  意义
001,Delivered
002,Pending
003,Failed
004,Resend

**功能目标：**根据这三个文件，输出每个用户的操作结果，输出内容如下。
\#用户名      结果码意义
RevanthReddy    Delivered
Kumar    Pending

**分析：**首先，我们这里涉及多个文件(在hive自定义UDF时，可以是多张表)，那么我们就要考虑是否适合使用DistributedCache来完成Join功能？这个怎么做呢，我一般是通过文件的大小，从三个文件的内容结构上，我们很容易判断，UserDetails和DeliveryDetails这两个文件的大小是在同一个量级上的，并且随着时间的推移会变得很大，所以显然不适合DistributedCache，而接着我们发现DeliveryStatusCodes这个文件是解释结果码的意义的，大小使固定不变的，并且不会很大，甚至可以说会很小很小，所以在这样的情况我们可以考虑使用DistributedCache缓存来完成Join，将DeliveryStatusCodes作为缓存文件。那剩下的两个文件怎么处理？这就可以涉及到MapReduce中的**MultipleInputs**这个接口，通过这个接口，我们可以实现多个Mapper类分别处理不同的输入文件。但这又会引出另一个问题，那就是reduce的实现定死只能有一个，这就意味着Mapper输出结果中，key的类型必须一致(或者说继承自同一个接口)，所以有必要将不同Mapper的输出key统一(这里场景恰好可以通过操作号这个值来达到这个目的)，并且要区分知道对应的value是属于哪个文件中的。顺着这种思路，我有了下面的代码实现。

*UserFileMapper.java-处理UserDetails.txt文件的Mapper类*

```java
package com.shell.join.reducesidejoin;

import java.io.IOException;

import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Mapper;
// 这个map很简单
public class UserFileMapper extends Mapper<LongWritable, Text, Text, Text> {
	private String cellNumber;
	private String userName;
	private String fileTag = "CD~";  // 通过这个标志来标识输出值是属于哪个map,将在reduce中看到作用
	
	@Override
	protected void map(LongWritable key, Text value, Mapper<LongWritable, Text, Text, Text>.Context context)
			throws IOException, InterruptedException {
		String line = value.toString();
		String[] splitArray = line.split(",");
		cellNumber = splitArray[0];
		userName = splitArray[1];
		
		context.write(new Text(cellNumber), new Text(fileTag + userName));
	}

}
```

*DeliveryFileMapper.java-处理DeliveryDetails.txt文件的Mapper类*

```java
package com.shell.join.reducesidejoin;

import java.io.IOException;

import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Mapper;

public class DeliverFileMapper extends Mapper<LongWritable, Text, Text, Text> {
	private String cellNumber;
	private String deliverCode;
	private String fileTag = "DR~";  // 跟UserFileMapper类中的作用一样

	@Override
	protected void map(LongWritable key, Text value, Mapper<LongWritable, Text, Text, Text>.Context context)
			throws IOException, InterruptedException {
		String line = value.toString();
		String[] splitArray = line.split(",");
		cellNumber = splitArray[0];
		deliverCode = splitArray[1];
		
		context.write(new Text(cellNumber), new Text(fileTag + deliverCode));
	}
}
```

*SmsReducer.java-实现join的reduce类*

```java
package com.shell.join.reducesidejoin;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;

import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.Reducer;

public class SmsReducer extends Reducer<Text, Text, Text, Text> {
	
	private HashMap<String, String> deliverCodesMap = new HashMap<>(); 
	private enum MYCOUNTER {
		RECORD_COUNT,
		FILE_EXISTS,
		FILE_NOT_FOUND,
		SOME_OTHER_ERROR
	}
	
	@Override
	protected void setup(Reducer<Text, Text, Text, Text>.Context context) throws IOException, InterruptedException {
        //这里前面有提到过,得到DistributedCache文件路径
		Path[] cacheFiles = Job.getInstance(context.getConfiguration()).getLocalCacheFiles();
		
		for (Path cacheFile : cacheFiles) {
			if (cacheFile.getName().trim().equals("DeliveryStatusCodes.txt")) {
				context.getCounter(MYCOUNTER.FILE_EXISTS).increment(1);
				loadDeliverStatusCodes(cacheFile, context);
			}
		}
	}
	// 读取缓存文件
	private void loadDeliverStatusCodes(Path cacheFile, Context context) {
		BufferedReader bufferedReader = null;
		
		try {
			bufferedReader = new BufferedReader(new FileReader(cacheFile.toString()));
			String line = null;
			while ((line = bufferedReader.readLine()) != null) {
				String[] splitArray = line.split(",");
				deliverCodesMap.put(splitArray[0], splitArray[1]);
			}
		} catch (FileNotFoundException e) {
			e.printStackTrace();
			context.getCounter(MYCOUNTER.FILE_NOT_FOUND).increment(1);
		} catch (IOException e) {
			e.printStackTrace();
			context.getCounter(MYCOUNTER.SOME_OTHER_ERROR).increment(1);
		} finally {
			if (bufferedReader != null) {
				try {
					bufferedReader.close();
				} catch (IOException e) {
					e.printStackTrace();
				} finally {
					bufferedReader = null;
				}
			}
		}
	}
	
    // 达到reduce的记录是这样的格式:{[操作号,(用户名,结果码)],[],...}
	@Override
	protected void reduce(Text key, Iterable<Text> values, Reducer<Text, Text, Text, Text>.Context context)
			throws IOException, InterruptedException {
		String userName = null;
		String deliverReport = null;
		for (Text value : values) {
			String splitArray[] = value.toString().split("~");
          
			// 通过指定前缀判断这个值是来自哪个mapper,从而可以知道对应的值是什么值(用户名或者结果码)
			if (splitArray[0].equals("CD")) {
				userName = splitArray[1];  // 获取用户名
			} else if (splitArray[0].equals("DR")) {
				deliverReport = deliverCodesMap.get(splitArray[1]); // 获取结果码对应的意义字符串
			}
		}
		// 输出结果
		if (userName != null && deliverReport != null) {
			context.write(new Text(userName), new Text(deliverReport));
		} else if (userName == null) {
			context.write(new Text("userName"), new Text(deliverReport));
		} else if (deliverReport == null) {
			context.write(new Text(userName), new Text("deliverReport"));
		}
	}
}
```

*SmsDriver.java-MapReduce主程序*

```java
package com.shell.join.reducesidejoin;

import org.apache.hadoop.conf.Configured;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.lib.input.MultipleInputs;
import org.apache.hadoop.mapreduce.lib.input.TextInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;
import org.apache.hadoop.mapreduce.lib.output.TextOutputFormat;
import org.apache.hadoop.util.Tool;
import org.apache.hadoop.util.ToolRunner;

public class SmsDriver extends Configured implements Tool {

	@Override
	public int run(String[] args) throws Exception {
		if (args.length != 4) {
			System.err.printf("Usage: %s [generic options] <input1> <input2> <output> <cachefile>\n", SmsDriver.class.getSimpleName());
			ToolRunner.printGenericCommandUsage(System.err);
			System.exit(-1);
		}
		
		Job job = Job.getInstance();
		job.setJarByClass(getClass());
		job.setJobName("SmsDriver");
		
		MultipleInputs.addInputPath(job, new Path(args[0]), TextInputFormat.class, UserFileMapper.class);
		MultipleInputs.addInputPath(job, new Path(args[1]), TextInputFormat.class, DeliverFileMapper.class);
		
		job.setReducerClass(SmsReducer.class);
		
		job.setOutputKeyClass(Text.class);
		job.setOutputValueClass(Text.class);
		
		job.setOutputFormatClass(TextOutputFormat.class);
		FileOutputFormat.setOutputPath(job, new Path(args[2]));
		
		job.addCacheFile(new Path(args[3]).toUri());
		
		return job.waitForCompletion(true) ? 0 : 1;
	}
	
	public static void main(String[] args) throws Exception {
		System.exit(ToolRunner.run(new SmsDriver(), args));
	}

}
```

**小结：**通过上述两个例子程序，我们知道了DistributedCache的使用，以及在什么情况下我们可以使用这个功能来帮助我们，同时我们也知道了通过MapReduce如何来完成Join功能。

### 2. 通过MultipleInputs以及多个Mapper完成Join

可能我们平常做得比较多的就是通过一个Mapper实现类来读取一个文件，然后处理，再Reducer这样子的一个过程，而比较忽略MultipleInputs这个接口。(至少我是这样，因为我没怎么弄过Hive、Hbase，这个功能在Hive的UDF中可能用的比较多)。通过MultipleInputs这个接口，在我们的MapReduce中，可以实现多个Mapper类，并且为每个类指定特定的输入文件目录和输入文件格式等。下面我要讲的就是如何通过多个Mapper实现类的方式来完成多个输入文件的Join功能。

**场景：**假设我现在有两个文件(并且都很大)，两个文件的内容分别如下。

*user.txt(记录唯一)*
user_id(唯一)	location_id
3241		1
3321		65
4532		13
7231		32
5321		34
8321		84
1342		21
3213		23
2134		9
2345		45
3423		36
7623		98
2346		87
2133		87

*transaction.txt*
transaction_id	product_id	user_id	quantity	amount
10	100	3241		2	200
11	101	3321		2	200
12	102	4532		2	200
13	100	7231		2	200
14	105	5321		2	200
15	107	8321		2	200
16	200	1342		2	200
17	109	3213		2	200
18	102	2134		2	200
19	106	2345		2	200
20	108	3423		2	200
21	200	7623		2	200
22	110	2346		2	200
23	100	2133		2	200
24	135	8773		2	200
25	201	8723		2	299
25	107	8724		2	287
26	103	3876		2	150

**功能需求：**根据这两个文件，输出每个用户购买产品所送达的目的地，输出格式如下。

user_id	product_id	location_id
3241		100	1
3321		101	65
....
3876		103	undefined(没有用户信息时，不知道location_id情况下)

类似于关系型数据库中left join之类的功能。

**分析：**从场景描述中，也已经知道这两个文件很大，所以我们可以摒弃DistributedCache这样的方式。那么我们将直接选择通过**MultipleInputs**方式来输入文件，并实现多个Mapper类。那么用这种实现方式伴随而来的问题是什么呢？那就是多个Mapper的输出key统一问题；因为我们这里是不同的Mapper处理不同的文件，那么就意味着不可能在mapper端完成Join，而只能选择在Reducer端完成join，在reduce中完成join其实会有一个很大的问题需要解决，因为mapper的输出结果，我要怎么样设置才可以使得需要join的记录出现在同一个处理节点上面，否则join将不完全。换句话说就是怎样设计Partitioner(也可以不用自己实现Partitioner，只要我们把需要join的Mapper结果的key统一之后，默认的hashPartitioner也一样会把这些记录shuffle到同一个reduce节点上面)。所以，最终问题就落在如何查找两个文件中的公有属性列(对应到关系型数据库中，就是找到join列)。这样问题就变得简单很多了。很明显，这里我们可以通过user_id这个列来统一mapper的输出key。

**代码实现：**

*LeftJoinTransaction.java-处理transaction.txt文件*

```java
package com.shell.dataalgorithms.mapreduce.chap04;

import java.io.IOException;

import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Mapper;

import edu.umd.cloud9.io.pair.PairOfStrings; // 这个类需要引入cloud9lib这个jar包

/**
 * input:
 *  <transaction_id><TAB><product_id><TAB><user_id><TAB><quantity><TAB><amount>
 * @author Administrator
 *
 */
public class LeftJoinTransactionMapper extends Mapper<LongWritable, Text, PairOfStrings, PairOfStrings> {
	
	PairOfStrings outputKey = new PairOfStrings();
	PairOfStrings outputValue = new PairOfStrings();
	
	@Override
	protected void map(LongWritable key, Text value,
			Mapper<LongWritable, Text, PairOfStrings, PairOfStrings>.Context context)
			throws IOException, InterruptedException {
		
		String[] tokens = value.toString().split("\t");
		String productId = tokens[1];
		String userId = tokens[2];
		
		outputKey.set(userId, "2");
		outputValue.set("P", productId);
		
		context.write(outputKey, outputValue);
	}

}
```

*LeftJoinUserMapper.java-处理user.txt文件的Mapper*

```java
package com.shell.dataalgorithms.mapreduce.chap04;

import java.io.IOException;

import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Mapper;

import edu.umd.cloud9.io.pair.PairOfStrings;

/**
 * input:
 *  <user_id><TAB><location_id>
 * @author Administrator
 *
 */
public class LeftJoinUserMapper extends Mapper<LongWritable, Text, PairOfStrings, PairOfStrings> {
	
	PairOfStrings outputKey = new PairOfStrings();
	PairOfStrings outputValue = new PairOfStrings();
	
	@Override
	protected void map(LongWritable key, Text value,
			Mapper<LongWritable, Text, PairOfStrings, PairOfStrings>.Context context)
			throws IOException, InterruptedException {
		
		String[] tokens = value.toString().split("\t");
		
		if (tokens.length == 2) {
			outputKey.set(tokens[0], "1");
			outputValue.set("L", tokens[1]);
			context.write(outputKey, outputValue);
		}
		
	}

}
```

*LeftJoinReducer.java-Reducer实现*

```java
package com.shell.dataalgorithms.mapreduce.chap04;

import java.io.IOException;
import java.util.Iterator;

import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Reducer;

import edu.umd.cloud9.io.pair.PairOfStrings;
/**
* 注意这里reducer跟上述两个例子中的不同,因为这里transaction文件中user_id是可以重复多次出现的,所以
* 这里reducer接收的记录将会是这样子的 [user_id,(location_id, product_id1,product_id2,...,product_idn)],...,[..]
**/
public class LeftJoinReducer extends Reducer<PairOfStrings, PairOfStrings, Text, Text> {
	
	
	@Override
	protected void reduce(PairOfStrings key, Iterable<PairOfStrings> values,
			Reducer<PairOfStrings, PairOfStrings, Text, Text>.Context context) throws IOException, InterruptedException {
		
		Text productId = new Text();
		Text locationId = new Text("undefined");
		Iterator<PairOfStrings> iterator = values.iterator();
		if (iterator.hasNext()) {
            // 这里没再单纯借用flag标志来区分记录值来之哪个mapper
            // 而是通过记录排序方式以及flag来区分记录值来自哪个mapper
			PairOfStrings firstPair = iterator.next();
			System.out.println("firstPair=" + firstPair);
			if (firstPair.getLeftElement().equals("L")) {
				locationId.set(firstPair.getRightElement());
			} else {
				context.write(new Text(firstPair.getRightElement()), locationId);
			}
		}
		
		while (iterator.hasNext()) {
			PairOfStrings productPair = iterator.next();
			System.out.println("productPair=" + productPair);
			productId.set(productPair.getRightElement());
			context.write(productId, locationId);
		}
	}

}
```

*LeftJoinDriver.java*

```java
package com.shell.dataalgorithms.mapreduce.chap04;

import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.lib.input.MultipleInputs;
import org.apache.hadoop.mapreduce.lib.input.TextInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;

import edu.umd.cloud9.io.pair.PairOfStrings;

public class LeftJoinDriver {

	public static void main(String[] args) throws Exception {
		Path transactions = new Path(args[0]);  // input
		Path users = new Path(args[1]); // input
		Path output = new Path(args[2]); // output
		
		Job job = Job.getInstance();
		job.setJarByClass(LeftJoinDriver.class);
		job.setJobName(LeftJoinDriver.class.getSimpleName());
		
		job.setPartitionerClass(SecondarySortPartitioner.class);
		
		job.setGroupingComparatorClass(SecondarySortGroupComparator.class);
		
		job.setReducerClass(LeftJoinReducer.class);
		
		job.setOutputKeyClass(Text.class);
		job.setOutputValueClass(Text.class);
//		job.setOutputFormatClass(SequenceFileOutputFormat.class);
		
		MultipleInputs.addInputPath(job, transactions, TextInputFormat.class, LeftJoinTransactionMapper.class);
		MultipleInputs.addInputPath(job, users, TextInputFormat.class, LeftJoinUserMapper.class);
		
		job.setMapOutputKeyClass(PairOfStrings.class);
		job.setMapOutputValueClass(PairOfStrings.class);
		FileOutputFormat.setOutputPath(job, output);
		
		job.waitForCompletion(true);
	}
}
```

*SecondarySortPartitioner.java*

```java
package com.shell.dataalgorithms.mapreduce.chap04;

import org.apache.hadoop.mapreduce.Partitioner;

import edu.umd.cloud9.io.pair.PairOfStrings;

public class SecondarySortPartitioner extends Partitioner<PairOfStrings, Object> {

	@Override
	public int getPartition(PairOfStrings key, Object value, int numPartitions) {
		return (key.getLeftElement().hashCode() & Integer.MAX_VALUE) % numPartitions;
	}

}
```

*SecondarySortGroupComparator.java*

```java
package com.shell.dataalgorithms.mapreduce.chap04;

import java.io.IOException;

import org.apache.hadoop.io.DataInputBuffer;
import org.apache.hadoop.io.RawComparator;

import edu.umd.cloud9.io.pair.PairOfStrings;

public class SecondarySortGroupComparator implements RawComparator<PairOfStrings> {

	@Override
	public int compare(PairOfStrings o1, PairOfStrings o2) {
		return o1.getLeftElement().compareTo(o2.getLeftElement());
	}

	@Override
	public int compare(byte[] b1, int s1, int l1, byte[] b2, int s2, int l2) {
		DataInputBuffer buffer = new DataInputBuffer();
		PairOfStrings a = new PairOfStrings();
		PairOfStrings b = new PairOfStrings();
		
		try {
			buffer.reset(b1, s1, l1);
			a.readFields(buffer);
			buffer.reset(b2, s2, l2);
			b.readFields(buffer);
			
			return compare(a, b);
		} catch (IOException e) {
			return -1;
		}
	}
	

}
```