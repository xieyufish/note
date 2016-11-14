## Spark处理Mysql数据源

### 1. 环境配置

​	要让Spark能够读取到Mysql数据库中的数据，那么首先必须的要Spark能够认识mysql这个数据源，这就需要连接mysql的jar包。这里我将java连接mysql的jar包拷贝到master机器中，并放置在目录/usr/local/spark-2.0/examples/jars里面，并且通过scp命令将此jar包拷贝到spark集群中其他节点上面，启动spark集群。

​	我通过spark-submit命令提交应用执行，命令如下：

**bin/spark-submit --class com.shell.hadoop.spark.sql.JdbcToMysqlExample --master spark://master:7077 --deploy-mode cluster examples/spark-0.0.1-SNAPSHOT.jar --jars examples/jars/mysql-connector-java-5.1.30.jar**

执行此命令，在浏览器中查看测试结构，始终失败，报告错误为：Not suitable driver，也就是spark找不到我这个mysql的连接jar包。

接着尝试命令：

**bin/spark-submit --class com.shell.hadoop.spark.sql.JdbcToMysqlExample --master spark://master:7077 --deploy-mode cluster examples/spark-0.0.1-SNAPSHOT.jar --driver-class-path examples/jars/mysql-connector-java-5.1.30.jar**

错误还是一样的，还是没有找到jar包。

**问题解决**

在spark的默认配置文件， conf/spark-default.conf文件中添加这两行： ![spark_29](images\spark_29.png)

再重新启动spark集群，运行命令：

**bin/spark-submit --class com.shell.hadoop.spark.sql.JdbcToMysqlExample --master spark://master:7077 --deploy-mode cluster examples/spark-0.0.1-SNAPSHOT.jar**

成功执行得到结果。

源代码粘贴如下：

```java
package com.shell.hadoop.spark.sql;

import static org.apache.spark.sql.functions.col;

import org.apache.spark.api.java.function.MapFunction;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Encoders;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.SparkSession;

public class JdbcToMysqlExample {
	public static void main(String[] args) {
		
		SparkSession sparkSession = SparkSession.builder().appName("JdbcToMysqlExample").getOrCreate();
		Dataset<Row> jdbcDF = sparkSession
								.read()
								.format("jdbc")
								.option("driver", "com.mysql.jdbc.Driver")
								.option("url", "jdbc:mysql://192.168.16.47:3306/ar?useUnicode=true&characterEncoding=utf8")
								.option("dbtable", "books")    // 必须要指定操作的mysql中的表名
								.option("user", "root")
								.option("password", "root")
								.load();
//		Dataset<Row> jdbcDF = sparkSession.read().format("jdbc").option("driver", "com.mysql.jdbc.Driver").option("url", "jdbc:mysql://192.168.16.47:3306/ar?useUnicode=true&characterEncoding=utf8").option("user", "root").option("password", "root").load();//报错
		
//		Dataset<Row> booksDF = sparkSession.sql("select * from books where id < 100");// 报错
//		booksDF.show();
		
		jdbcDF.groupBy("category_id").count().show(); // 根据category_id分类统计
		
		Dataset<Row> filterDF = jdbcDF.filter(col("id").lt(100));  // 过滤mysql表中id<100的数据
		
		Dataset<Book> books = filterDF.map(new MapFunction<Row, Book>() { // 执行map操作

			@Override
			public Book call(Row row) throws Exception {
				Integer id = row.<Integer>getAs("id");
				String name = row.<String>getAs("name");
				String xmlUrl = row.<String>getAs("xml_url");
				
				Book book = new Book();
				book.setId(id);
				book.setName(name);
				book.setXmlUrl(xmlUrl);
				return book;
			}
			
		}, Encoders.bean(Book.class));
		
		books.show();  // 打印结果
		
//		Dataset<Row> bookContents = sparkSession.sql("select * from book_contents");
//		bookContents.show();
		
		
	}
}
```