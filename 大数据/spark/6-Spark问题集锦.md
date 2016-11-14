## Spark问题集锦

### 1. Spark on yarn-cluster出现的问题

Spark应用以yarn-cluster模式运行时，控制台打印的错误信息：Container exited with a non-zero exit code 15   Exception in thread "main" org.apache.spark.SparkException: Application application_1475055439418_0008 finished with failed status  ![spark_3](images\spark_3.png)![spark_2](images\spark_2.png)

从这个控制台打印的信息， 我们根本得不到具体的错误原因是什么。在这种情况下，我们应该怎么去查找准确的日志信息呢？其实从打印的信息我们就只，可以通过浏览器输入http://master:8088/proxy/application_1475055439418_0002/Then来查看具体的错误日志信息： ![spark_4](images\spark_4.png)

我的程序代码为：

```java
package com.shell.hadoop.spark;

import java.util.ArrayList;
import java.util.List;
import org.apache.spark.api.java.JavaRDD;
import org.apache.spark.api.java.JavaSparkContext;
import org.apache.spark.api.java.function.Function;
import org.apache.spark.api.java.function.Function2;


public class ComplexJob {
	public static void main(String[] args) {
		JavaSparkContext sparkContext = new JavaSparkContext("spark://master:7077", "complexJob");
		
		int slices = (args.length == 1) ? Integer.parseInt(args[0]) : 2;
		int n = 100000 * slices;
		List<Integer> l = new ArrayList<>();
		for (int i = 0; i < n; i++) {
			l.add(i);
		}
		
		JavaRDD<Integer> dataSet = sparkContext.parallelize(l, slices);
		int count = dataSet.map(new Function<Integer, Integer>() {
			private static final long serialVersionUID = -5146745675334154169L;

			@Override
			public Integer call(Integer v1) throws Exception {
				double x = Math.random() * 2 - 1;
				double y = Math.random() * 2 - 1;
				return (x * x + y * y < 1) ? 1 : 0;
			}
			
		}).reduce(new Function2<Integer, Integer, Integer>() {

			private static final long serialVersionUID = -5862061911552056025L;

			@Override
			public Integer call(Integer v1, Integer v2) throws Exception {
				
				return v1 + v2;
			}
			
		});
		
		System.out.println("Pi is roughly " + 4.0 * count / n);
		
		sparkContext.stop();
		sparkContext.close();
	}
}
```

所以问题就出来了：com.shell.hadoop.spark.ComplexJob\$2是一个内部匿名类，也就是上述代码中的new Function2()这个类,我们知道这种情况下，只要ComplexJob这个类存在ComplexJob$2是不可能会找不到的(除非自己手贱手动把他删除了)。所以这也是我想不清会报这个错误的原因。因为我代码是跟Spark提供的例子程序JavaSparkPi实现的功能一样的，所以我用相同的命令运行了**JavaSparkPi**这个例子程序，发现是可以成功运行得到结果的。我就想事我的代码出现问题了，结果真的，我创建**JavaSparkContext**这个实例的代码是不一样的。修改后代码如下：

```java
package com.shell.hadoop.spark;

import java.util.ArrayList;
import java.util.List;

import org.apache.spark.api.java.JavaRDD;
import org.apache.spark.api.java.JavaSparkContext;
import org.apache.spark.api.java.function.Function;
import org.apache.spark.api.java.function.Function2;


public class ComplexJob {
	public static void main(String[] args) {
		JavaSparkContext sparkContext = new JavaSparkContext();  // 修改之后的,不要提供spark的master信息
		
		int slices = (args.length == 1) ? Integer.parseInt(args[0]) : 2;
		int n = 100000 * slices;
		List<Integer> l = new ArrayList<>();
		for (int i = 0; i < n; i++) {
			l.add(i);
		}
		
		JavaRDD<Integer> dataSet = sparkContext.parallelize(l, slices);
		int count = dataSet.map(new Function<Integer, Integer>() {
			private static final long serialVersionUID = -5146745675334154169L;

			@Override
			public Integer call(Integer v1) throws Exception {
				double x = Math.random() * 2 - 1;
				double y = Math.random() * 2 - 1;
				return (x * x + y * y < 1) ? 1 : 0;
			}
			
		}).reduce(new Function2<Integer, Integer, Integer>() {

			private static final long serialVersionUID = -5862061911552056025L;

			@Override
			public Integer call(Integer v1, Integer v2) throws Exception {
				
				return v1 + v2;
			}
			
		});
		
		System.out.println("Pi is roughly " + 4.0 * count / n);
		
		sparkContext.stop();
		sparkContext.close();
	}
}
```

重新打包，成功运行得到结果。出现这个问题的具体原因还没有分析，应该是在于yarn-cluster和yarn-client两种运行模式的区别。先记录这个问题，接着再深入研究。