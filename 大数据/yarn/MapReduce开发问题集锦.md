## MapReduce程序开发过程-问题集锦

### 1. 第三方依赖jar包问题

**问题描述：**在编写MapReduce程序时，涉及到业务实现需要依赖于第三方jar包，由于这些jar包不包含在集群节点的classpath中，所以会报各种**ClassNotFoundException**。

**解决过程：**因为我使用eclipse开发工具，并基于maven构建的项目，而第三方依赖也是通过maven的依赖引入来解决所需依赖包问题的。因为我现在项目中引入了**Tika**依赖(文档分析抽取工具)，所以我需要将Tika相关的依赖打包到生成的jar包中，并提交到集群运行。因为平时只使用maven来解决依赖包的问题，所以对于maven的打包机制并没有深入学习，所以这里暂时只提供最终的解决方式。将项目的pom.xml修改为如下：

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
	xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
	<modelVersion>4.0.0</modelVersion>

	<groupId>com.shell</groupId>
	<artifactId>hadoop-examples</artifactId>
	<version>1.0</version>
	<packaging>jar</packaging>

	<name>hadoop-examples</name>
	<url>http://maven.apache.org</url>

	<properties>
		<project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
		<jdk.version>1.7</jdk.version>
	</properties>

	<dependencies>
		<dependency>
			<groupId>junit</groupId>
			<artifactId>junit</artifactId>
			<version>3.8.1</version>
			<scope>test</scope>
		</dependency>

		<dependency>
			<groupId>org.apache.hadoop</groupId>
			<artifactId>hadoop-common</artifactId>
			<version>2.6.4</version>
            <!-- 指定这个域为provided,这样在执行package命令时,不会把这个依赖打入到最终的jar包中 -->
			<scope>provided</scope> 
		</dependency>
		<dependency>
			<groupId>org.apache.hadoop</groupId>
			<artifactId>hadoop-hdfs</artifactId>
			<version>2.6.4</version>
			<scope>provided</scope>
		</dependency>
		<dependency>
			<groupId>org.apache.hadoop</groupId>
			<artifactId>hadoop-client</artifactId>
			<version>2.6.4</version>
			<scope>provided</scope>
		</dependency>
        <!-- 因为现在需要将下面两个依赖打入到最终的jar包中,所以这里取默认scope即可 -->
		<dependency>
			<groupId>org.apache.tika</groupId>
			<artifactId>tika-core</artifactId>
			<version>1.14</version>
		</dependency>
		<dependency>
			<groupId>org.apache.tika</groupId>
			<artifactId>tika-parsers</artifactId>
			<version>1.14</version>
		</dependency>

	</dependencies>

	<build>
		<plugins>
			<plugin>
				<groupId>org.apache.maven.plugins</groupId>
				<artifactId>maven-compiler-plugin</artifactId>
				<configuration>
					<source>${jdk.version}</source>
					<target>${jdk.version}</target>
				</configuration>
			</plugin>
			<plugin>
             <!-- 配置assembly的打包方式(奇怪的是,配置这种打包方式之后,默认的jar打包方式也会执行) -->
             <!-- 也就是我基于这个配置环境,最终生成的jar包有两个 -->
				<artifactId>maven-assembly-plugin</artifactId>
				<configuration>
					<descriptorRefs>
						<descriptorRef>jar-with-dependencies</descriptorRef>
					</descriptorRefs>
                    <!-- 下面这个配置指定jar包的入口主程序,视情况可配 -->
                    <!-- 
				    当我们的项目是要完成多个功能时, 像我在学习时就是多个mapreduce在一个项目中,
                    建议不要配置这个选项,否则在集群用hadoop命令执行提交时,会出现问题,如果配置了
                    这个选项(值就值下面的值),那么我们在集群用hadoop命令执行提交时不用指定要执行的
                    主类名了,否则将不能执行成功,如果没有配置这个选择则需要指定主类名
                     -->
					<!-- <archive>
						<manifest>
							<mainClass>com.shell.tika.TikaMapReduce</mainClass>
						</manifest>
					</archive> -->
				</configuration>
				<executions>
					<execution>
						<id>make-assembly</id>
						<phase>package</phase>
						<goals>
							<goal>single</goal>
						</goals>
					</execution>
				</executions>
			</plugin>
		</plugins>
	</build>
</project>
```

将pom.xml修改为上面的形式之后，执行maven的package命令，将会把上面tika的两个依赖以及他们的依赖全部打入到最终生成的jar文件中(在上述配置，是在带jar-with-dependencies后缀的jar包中)。

按上述方式打包之后，问题成功解决。

### 2. 使用URL解析hdfs协议时

**问题描述：**在mapreduce中，当通过java.net.URL.URL去构建一个hdfs字符开头的path路径时，报不能认识这个协议的异常，具体异常信息如下：

 ![yarn_2](images\yarn_2.png)

```
Error: java.net.MalformedURLException: unknown protocol: hdfs
	at java.net.URL.<init>(URL.java:596)
	at java.net.URL.<init>(URL.java:486)
	at java.net.URL.<init>(URL.java:435)
	at com.shell.tika.TikaRecordReader.nextKeyValue(TikaRecordReader.java:37)
	at org.apache.hadoop.mapred.MapTask$NewTrackingRecordReader.nextKeyValue(MapTask.java:553)
	at org.apache.hadoop.mapreduce.task.MapContextImpl.nextKeyValue(MapContextImpl.java:80)
	at org.apache.hadoop.mapreduce.lib.map.WrappedMapper$Context.nextKeyValue(WrappedMapper.java:91)
	at org.apache.hadoop.mapreduce.Mapper.run(Mapper.java:144)
	at org.apache.hadoop.mapred.MapTask.runNewMapper(MapTask.java:784)
	at org.apache.hadoop.mapred.MapTask.run(MapTask.java:341)
	at org.apache.hadoop.mapred.YarnChild$2.run(YarnChild.java:163)
	at java.security.AccessController.doPrivileged(Native Method)
	at javax.security.auth.Subject.doAs(Subject.java:415)
	at org.apache.hadoop.security.UserGroupInformation.doAs(UserGroupInformation.java:1656)
	at org.apache.hadoop.mapred.YarnChild.main(YarnChild.java:158)
```

**异常原因：**这是因为java.net.URL.URL这个类只能对常见的网络协议格式进行解析，比如http，https等，而hdfs是hadoop中的文件访问协议，URL不支持对这个协议的解析，所以才引发的这个异常。

**解决方式：**添加如下代码：URL.setURLStreamHandlerFactory(new FsUrlStreamHandlerFactory());即可让URL识别hdfs这个协议。代码片段如下：

```java
Path path = fileSplit.getPath();
key.set(path.toString());

try {
  URL.setURLStreamHandlerFactory(new FsUrlStreamHandlerFactory()); //必须指定
  String con = new Tika().parseToString(new URL(path.toString()));
  String string = con.replaceAll("[$%&+,:;=?#|']", " ");
  String string2 = string.replaceAll("\\s+", " ");
  String low = string2.toLowerCase();
  value.set(low);
} catch (TikaException e) {
  e.printStackTrace();
}
```

### 3. 自定义输出key类型时(Unable to initialize any output collector)

**问题描述：**当在Mapper端以自定义的类型作为Map的输出key时，总是会报如下错误： 

![yarn_3](images\yarn_3.png)

```
16/11/24 14:35:42 INFO mapreduce.Job: Task Id : attempt_1479714717980_0051_m_000000_2, Status : FAILED
Error: java.io.IOException: Unable to initialize any output collector
	at org.apache.hadoop.mapred.MapTask.createSortingCollector(MapTask.java:412)
	at org.apache.hadoop.mapred.MapTask.access$100(MapTask.java:81)
	at org.apache.hadoop.mapred.MapTask$NewOutputCollector.<init>(MapTask.java:695)
	at org.apache.hadoop.mapred.MapTask.runNewMapper(MapTask.java:767)
	at org.apache.hadoop.mapred.MapTask.run(MapTask.java:341)
	at org.apache.hadoop.mapred.YarnChild$2.run(YarnChild.java:163)
	at java.security.AccessController.doPrivileged(Native Method)
	at javax.security.auth.Subject.doAs(Subject.java:415)
	at org.apache.hadoop.security.UserGroupInformation.doAs(UserGroupInformation.java:1656)
	at org.apache.hadoop.mapred.YarnChild.main(YarnChild.java:158)
```

发生错误的代码部分：

```java
package com.shell.custom.datatype.example2;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.io.WritableComparable;

public class WebLog implements WritableComparable<WebLog> {
	private Text siteUrl;
	private Text ip;
	private Text timestamp;
	private Text reqDate;
	private IntWritable reqNo;

	public WebLog(String siteUrl, String ip, String timestamp, String reqDate, int reqNo) {
		this.siteUrl = new Text(siteUrl);
		this.ip = new Text(ip);
		this.timestamp = new Text(timestamp);
		this.reqDate = new Text(reqDate);
		this.reqNo = new IntWritable(reqNo);
	}

	@Override
	public void write(DataOutput out) throws IOException {
		siteUrl.write(out);
		ip.write(out);
		timestamp.write(out);
		reqDate.write(out);
		reqNo.write(out);
	}

	@Override
	public void readFields(DataInput in) throws IOException {
		siteUrl.readFields(in);
		ip.readFields(in);
		timestamp.readFields(in);
		reqDate.readFields(in);
		reqNo.readFields(in);
	}

	public Text getSiteUrl() {
		return siteUrl;
	}

	public void setSiteUrl(Text siteUrl) {
		this.siteUrl = siteUrl;
	}

	public Text getIp() {
		return ip;
	}

	public void setIp(Text ip) {
		this.ip = ip;
	}

	public Text getTimestamp() {
		return timestamp;
	}

	public void setTimestamp(Text timestamp) {
		this.timestamp = timestamp;
	}

	public Text getReqDate() {
		return reqDate;
	}

	public void setReqDate(Text reqDate) {
		this.reqDate = reqDate;
	}

	public IntWritable getReqNo() {
		return reqNo;
	}

	public void setReqNo(IntWritable reqNo) {
		this.reqNo = reqNo;
	}

	@Override
	public int compareTo(WebLog o) {
		if (ip.compareTo(o.ip) == 0) {
			return timestamp.compareTo(o.timestamp);
		} else {
			return ip.compareTo(o.ip);
		}
	}
	
	@Override
	public boolean equals(Object obj) {
		if (obj instanceof WebLog) {
			WebLog other = (WebLog) obj;
			return ip.equals(other.ip) && timestamp.equals(other.timestamp);
		}
		return false;
	}
	
	@Overrid
	public int hashCode() {
		return ip.hashCode();
	}
}
```

**异常原因：**因为我自定义的key实现类WebLog，当Map任务执行完毕时，将输出写入到了本地的磁盘文件中，当从磁盘中反序列这些map的输出结果是，要调用WebLog的无参构造函数来new一个实例，在调用readFields(in)方法来初始化成员变量，因为我没有定义一个无参构造函数，因此不能new WebLog实例，从而导致了这个问题发生。

**解决方式：**在WebLog类中增加无参构造函数，问题完美解决。

**注意：**在遇到这个异常之前，也遇到过相同提示错误的异常，那次是因为包引用错误导致的这个问题发生，所以这次我以为也是同样的原因引起的这个问题，所以把思路定位在了查找包引用方面，导致耗费了脑力和时间，最终通过搜索解决的这个问题。

### 4. 有Iterator.hasNext()引发的死循环

**问题描述：**在做一个自定义Partitioner的过程中，在集群上跑任务时，任务指定到reduce这一步是，任务不再执行下去，一直定在哪里，情况如下：

![yarn_4](images\yarn_4.png)

```
16/11/24 15:26:06 INFO client.RMProxy: Connecting to ResourceManager at master/192.168.146.146:8032
16/11/24 15:26:06 WARN mapreduce.JobResourceUploader: Hadoop command-line option parsing not performed. Implement the Tool interface and execute your application with ToolRunner to remedy this.
16/11/24 15:26:07 INFO input.FileInputFormat: Total input paths to process : 1
16/11/24 15:26:07 INFO mapreduce.JobSubmitter: number of splits:1
16/11/24 15:26:07 INFO mapreduce.JobSubmitter: Submitting tokens for job: job_1479714717980_0054
16/11/24 15:26:07 INFO impl.YarnClientImpl: Submitted application application_1479714717980_0054
16/11/24 15:26:07 INFO mapreduce.Job: The url to track the job: http://master:8088/proxy/application_1479714717980_0054/
16/11/24 15:26:07 INFO mapreduce.Job: Running job: job_1479714717980_0054
16/11/24 15:26:13 INFO mapreduce.Job: Job job_1479714717980_0054 running in uber mode : false
16/11/24 15:26:13 INFO mapreduce.Job:  map 0% reduce 0%
16/11/24 15:26:18 INFO mapreduce.Job:  map 100% reduce 0%
16/11/24 15:26:30 INFO mapreduce.Job:  map 100% reduce 70%
```

异常代码片段：

```java
static class PartitionerReduce extends Reducer<Text, Text, Text, IntWritable> {
		@Override
		protected void reduce(Text key, Iterable<Text> values, Reducer<Text, Text, Text, IntWritable>.Context context)
				throws IOException, InterruptedException {
			int gameCount=0;
            Iterator<Text> itr = values.iterator();
			while(itr.hasNext()) {  // 异常代码行  或者直接这样:values.iterator().hasNext()
				gameCount++;
			}
			context.write(new Text(key),new IntWritable(gameCount));
		}
}
```

**异常分析：**这是由于对Iterator的实现逻辑忘记引起的悲剧，本以为Iterator的hasNext()方法会让内部游标移动，但是查看源码发现hasNext是不会改变内部游标的值得，他只是执行简单的比较逻辑，游标移动是在next()方法中实现的，所以在上面情况中，只执行了hasNext方法，导致游标没动，一直在哪里循环。下面贴出ArrayList中Iterator的hasNext和next的实现代码：

```java
public boolean hasNext() {
    return cursor != size;
}

@SuppressWarnings("unchecked")
public E next() {
    checkForComodification();
    int i = cursor;
    if (i >= size)
        throw new NoSuchElementException();
    Object[] elementData = ArrayList.this.elementData;
    if (i >= elementData.length)
        throw new ConcurrentModificationException();
    cursor = i + 1;
    return (E) elementData[lastRet = i];
}
```

**解决方式：**修改迭代方式即可，可以使用foreach或者在while中执行next方法均可。