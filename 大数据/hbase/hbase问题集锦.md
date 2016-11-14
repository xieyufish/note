## HBase问题集锦

### 1. state=FAILED_OPEN(SNAPPY压缩方式错误)

**代码**

```java
package com.shell.hadoop.hbase;

import java.io.IOException;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.Cell;
import org.apache.hadoop.hbase.CellUtil;
import org.apache.hadoop.hbase.HBaseConfiguration;
import org.apache.hadoop.hbase.HColumnDescriptor;
import org.apache.hadoop.hbase.HConstants;
import org.apache.hadoop.hbase.HTableDescriptor;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.client.Admin;
import org.apache.hadoop.hbase.client.Connection;
import org.apache.hadoop.hbase.client.ConnectionFactory;
import org.apache.hadoop.hbase.client.Get;
import org.apache.hadoop.hbase.client.Put;
import org.apache.hadoop.hbase.client.Result;
import org.apache.hadoop.hbase.client.Table;
import org.apache.hadoop.hbase.io.compress.Compression.Algorithm;

public class Example {
	
	private static final String TABLE_NAME = "example_table";
	private static final String CF_DEFAULT = "default_table_family";
	
	public static void createOrOverwrite(Admin admin, HTableDescriptor table) throws IOException {
		if (admin.tableExists(table.getTableName())) {
			admin.disableTable(table.getTableName());
			admin.deleteTable(table.getTableName());
		}
		
		admin.createTable(table);
		
	}
	
	public static void createSchemaTables(Configuration config)  {
		try (Connection connection = ConnectionFactory.createConnection(config);
			 Admin admin = connection.getAdmin()) {
			
			HTableDescriptor table = new HTableDescriptor(TableName.valueOf(TABLE_NAME));
			table.addFamily(new HColumnDescriptor(CF_DEFAULT).setCompactionCompressionType(Algorithm.SNAPPY));  // 这里的压缩算法是问题的关键
			
			System.out.println("Creating table...");
			if (admin.tableExists(TableName.valueOf(TABLE_NAME))) {
				admin.disableTable(TableName.valueOf(TABLE_NAME));
				admin.deleteTable(TableName.valueOf(TABLE_NAME));
			}
			admin.createTable(table);
			System.out.println("Creating done");
			
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	public static void insertData(Configuration config) {
		try(Connection connection = ConnectionFactory.createConnection(config);
			Admin admin = connection.getAdmin()) {
			
			TableName tableName = TableName.valueOf(TABLE_NAME);
			if (!admin.tableExists(tableName)) {
				System.out.println("Table does not exist.");
				System.exit(1);
			}
			
			Table table = connection.getTable(tableName);
			Put put = new Put("first".getBytes());
			put.addColumn(CF_DEFAULT.getBytes(), "col1".getBytes(), "value1".getBytes());
			
			System.out.println("Insert data...");
			table.put(put);
			
			table.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	public static void getData(Configuration config) {
		try(Connection connection = ConnectionFactory.createConnection(config)) {
			
			Table table = connection.getTable(TableName.valueOf(TABLE_NAME));
			Get get = new Get("first".getBytes());
			
			Result result = table.get(get);
			for (Cell cell : result.rawCells()) {
				System.out.println("--------------------" + new String(CellUtil.cloneRow(cell)) + "-----------");
				System.out.println("Column Family: " + new String(CellUtil.cloneFamily(cell)));
				System.out.println("Column       : " + new String(CellUtil.cloneQualifier(cell)));
				System.out.println("Value        : " + new String(CellUtil.cloneValue(cell)));
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	public static void modifySchema(Configuration config) {
		try (Connection connection = ConnectionFactory.createConnection(config);
			 Admin admin = connection.getAdmin()) {
			
			TableName tableName = TableName.valueOf(TABLE_NAME);
			if (!admin.tableExists(tableName)) {
				System.out.println("Table does not exist.");
				System.exit(-1);
			}
			
			HTableDescriptor table = new HTableDescriptor(tableName);
			
			HColumnDescriptor newColumn = new HColumnDescriptor("NEWCF");
			newColumn.setCompactionCompressionType(Algorithm.SNAPPY);
			newColumn.setMaxVersions(HConstants.ALL_VERSIONS);
			admin.addColumn(tableName, newColumn);
			
			HColumnDescriptor existingColumn = new HColumnDescriptor(CF_DEFAULT);
			existingColumn.setCompactionCompressionType(Algorithm.SNAPPY);
			existingColumn.setMaxVersions(HConstants.ALL_VERSIONS);
			table.modifyFamily(existingColumn);
			admin.modifyTable(tableName, table);
			
			admin.disableTable(tableName);
			
			admin.deleteColumn(tableName, CF_DEFAULT.getBytes());
			
			admin.deleteTable(tableName);
			
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	public static void main(String[] args) {
		Configuration config = HBaseConfiguration.create();
		
		config.addResource("hadoop/core-site.xml");
		config.addResource("hbase/hbase-site.xml");
		createSchemaTables(config); //创建表格
		insertData(config);  //插入数据
		getData(config);  //获取数据
		
	}
}
```

**问题表现**

​	运行程序之后，程序停止在插入数据这一步操作不再前进，控制台也没有任何的异常信息打印输出。通过浏览器查看HBase状态，情况如下： ![1](images\1.png)

**解决过程**

​	通过浏览器查看到异常信息的情况之后，就始终把错误归结在**state=FAILED_OPEN**这个原因上面。在网上查找这方面错误的原因，有说是因为regionserver的状态出现问题，zookeeper没有跟踪到hbase节点的信息等等情况，尝试了网上提供的hbase自检工具命令：**bin/hbase  hbck**，修复命令：**bin/hbase hbck -repair**等其他相关的命令均不能解决我的问题；最终还尝试将创建的表删除，删除hdfs上的表文件，删除在zookeeper上的表节点，清理hbasemeta表中有关表的行数据，再重新运行程序；结果依然没有变化，还是停止在插入数据这一步，表现完全没有改变。最后，看到网友提示查看了各个节点上详细的输出日志信息，发现了猫腻： ![2](images\2.png)

​	节点的输出日志我们可以看到，**FAILED_OPEN**是由**snappy**处理算法引起的，所以我尝试将代码中的snappy算法改为**GZ**算法，OK，程序完美运行。

**错误原因**

​	出现这个错误的原因是：HBase因为版权问题没有将snappy算法的库添加进来，所以如果我们要用到这个算法就需要自己手动添加并设置HBASE_LIBRARY_PATH环境变量，以指明snappy库的位置。snappy的安装配置是一个相当复杂的过程，这里不讲，可以参考一下几篇文章：http://louishust.github.io/hbase/2015/07/14/snappy-install/，http://www.cnblogs.com/shitouer/archive/2013/01/14/2859475.html