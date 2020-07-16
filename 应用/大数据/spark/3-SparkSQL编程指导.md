## SparkSQL编程指导

[TOC]

### 1. Overview

​	Spark SQL是spark提供的一个结构化数据处理模块。Spark提供的SparkSQL接口主要是针对数据的结构化及其计算，并针对这些方面做了大量的优化处理。SparkSQL提供了两种方式来让我们操作结构化数据：SQL和Dataset API。

### 2. SQL

​	SparkSQL可以直接执行sql查询，Spark SQL也可以从已经存在的hive中读取数据（关于这部分的配置在下面的模块讲）。Spark SQL也可以通过命令行和JDBC/ODBC的方式来交互。

### 3. Datasets and DataFrames

​	Dataset是一个分布式数据集，Spark1.6中加入的新接口。Dataset可以通过JVM对象来构造，并且可以使用功能转换（functional transformations（map，flatmap，filter等））操作。Dataset与RDDs类似，Dataset通过**Encoder**的方式序列化处理对象并在网络上传输而不是Java serialization或者Kryo方式。尽管encoder和标准序列化方式都是将对象转换为字节，但是encoder是动态生成代码的，并且允许spark在不需要反序列化的情况下执行许多操作，比如：过滤，排序，哈希等。

​	DataFrame由Dataset\<Row\>组成。在概念上与关系型数据库的table一样，但是在底层有更丰富的优化措施。DataFrame可以通过多个不同的数据源来构造，比如：结构化数据文件，Hive中的table，外部数据库或者是存在的RDDs。

### 4. Getting Started

#### 4.1 起点：SparkSession

​	在Spark中，SparkSession是所有功能的入口起点（2.0之后吧）。我们可以通过SparkSession.builder()来创建SparkSession的实例：

```java
import org.apache.spark.sql.SparkSession;

SparkSession spark = SparkSession
  .builder()
  .appName("Java Spark SQL basic example")
  .config("spark.some.config.option", "some-value")
  .getOrCreate();
```

​	在Spark2.0中，SparkSession提供了内置的针对Hive的支持，包括使用HiveQL查询，访问Hive UDFs，读取Hive tables中的数据，使用这些特色功能，也不再需要一个安装好的Hive。

#### 4.2 创建Datasets

```java
import java.util.Arrays;
import java.util.Collections;
import java.io.Serializable;

import org.apache.spark.api.java.function.MapFunction;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.Encoder;
import org.apache.spark.sql.Encoders;

public static class Person implements Serializable {
  private String name;
  private int age;

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public int getAge() {
    return age;
  }

  public void setAge(int age) {
    this.age = age;
  }
}

// 1. 从bean对象创建dataset
// Create an instance of a Bean class
Person person = new Person();
person.setName("Andy");
person.setAge(32);

// Encoders are created for Java beans
// Encoder方式序列化
Encoder<Person> personEncoder = Encoders.bean(Person.class);
Dataset<Person> javaBeanDS = spark.createDataset(
  Collections.singletonList(person),
  personEncoder
);
javaBeanDS.show();
// +---+----+
// |age|name|
// +---+----+
// | 32|Andy|
// +---+----+

// 2. 从普通类型创建dataset
// Encoders for most common types are provided in class Encoders
Encoder<Integer> integerEncoder = Encoders.INT();
Dataset<Integer> primitiveDS = spark.createDataset(Arrays.asList(1, 2, 3), integerEncoder);
Dataset<Integer> transformedDS = primitiveDS.map(new MapFunction<Integer, Integer>() {
  @Override
  public Integer call(Integer value) throws Exception {
    return value + 1;
  }
}, integerEncoder);
transformedDS.collect(); // Returns [2, 3, 4]

// 3. 从文件创建dataset
// DataFrames can be converted to a Dataset by providing a class. Mapping based on name
// 将dataFrame转换为dataset
String path = "examples/src/main/resources/people.json";
Dataset<Person> peopleDS = spark.read().json(path).as(personEncoder);
peopleDS.show();
// +----+-------+
// | age|   name|
// +----+-------+
// |null|Michael|
// |  30|   Andy|
// |  19| Justin|
// +----+-------+
```

#### 4.3 创建DataFrames

​	应用通过SparkSession，可以从存在的RDD，Hive table或者是Spark data sources来创建DataFrame。

**通过json文件创建DataFrame**

```java
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;

Dataset<Row> df = spark.read().json("examples/src/main/resources/people.json");

// Displays the content of the DataFrame to stdout
df.show();
// +----+-------+
// | age|   name|
// +----+-------+
// |null|Michael|
// |  30|   Andy|
// |  19| Justin|
// +----+-------+
```

**DataFrame操作**

```java
// col("...") is preferable to df.col("...")
import static org.apache.spark.sql.functions.col;

// Print the schema in a tree format
df.printSchema();
// root
// |-- age: long (nullable = true)
// |-- name: string (nullable = true)

// Select only the "name" column
df.select("name").show();
// +-------+
// |   name|
// +-------+
// |Michael|
// |   Andy|
// | Justin|
// +-------+

// Select everybody, but increment the age by 1
df.select(col("name"), col("age").plus(1)).show();
// +-------+---------+
// |   name|(age + 1)|
// +-------+---------+
// |Michael|     null|
// |   Andy|       31|
// | Justin|       20|
// +-------+---------+

// Select people older than 21
df.filter(col("age").gt(21)).show();
// +---+----+
// |age|name|
// +---+----+
// | 30|Andy|
// +---+----+

// Count people by age
df.groupBy("age").count().show();
// +----+-----+
// | age|count|
// +----+-----+
// |  19|    1|
// |null|    1|
// |  30|    1|
// +----+-----+
```

**执行SQL查询**

​	SparkSession的sql函数可以运行SQL查询语句。

```java
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;

// Register the DataFrame as a SQL temporary view
df.createOrReplaceTempView("people");

Dataset<Row> sqlDF = spark.sql("SELECT * FROM people");
sqlDF.show();
// +----+-------+
// | age|   name|
// +----+-------+
// |null|Michael|
// |  30|   Andy|
// |  19| Justin|
// +----+-------+
```

### 5. 和RDD交互

​	Spark SQL提供两种不同的方法来将存在的RDD转换为Datasets。

- 反射方式：通过反射可以推测一个包含特殊类型的RDD的schema。反射方式是基于我们知道具体类型的情况下使用的，并且使用更少的代码。
- 编程接口：这种方式是我们要先创建一个schema，然后把这个schema运用到存在的RDD上。

**反射方式**

​	Spark SQL支持自动将一个JavaBean类型的RDD转换为一个DataFrame。JavaBean的信息将通过反射的方式来确定将要创建的DataFrame的schema。当前，Spark SQL不支持JavaBean中包含Map属性，内置的JavaBean和List或Array属性都支持。

```java
import org.apache.spark.api.java.JavaRDD;
import org.apache.spark.api.java.function.Function;
import org.apache.spark.api.java.function.MapFunction;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.Encoder;
import org.apache.spark.sql.Encoders;

// 创建一个javabean为Person的RDD
// Create an RDD of Person objects from a text file
JavaRDD<Person> peopleRDD = spark.read()
  .textFile("examples/src/main/resources/people.txt")
  .javaRDD()
  .map(new Function<String, Person>() {
    @Override
    public Person call(String line) throws Exception {
      String[] parts = line.split(",");
      Person person = new Person();
      person.setName(parts[0]);
      person.setAge(Integer.parseInt(parts[1].trim()));
      return person;
    }
  });

// 由RDD来创建Dataframe
// Apply a schema to an RDD of JavaBeans to get a DataFrame
Dataset<Row> peopleDF = spark.createDataFrame(peopleRDD, Person.class);
// Register the DataFrame as a temporary view
peopleDF.createOrReplaceTempView("people");

// SQL statements can be run by using the sql methods provided by spark
Dataset<Row> teenagersDF = spark.sql("SELECT name FROM people WHERE age BETWEEN 13 AND 19");

// The columns of a row in the result can be accessed by field index
Encoder<String> stringEncoder = Encoders.STRING();
Dataset<String> teenagerNamesByIndexDF = teenagersDF.map(new MapFunction<Row, String>() {
  @Override
  public String call(Row row) throws Exception {
    return "Name: " + row.getString(0);
  }
}, stringEncoder);
teenagerNamesByIndexDF.show();
// +------------+
// |       value|
// +------------+
// |Name: Justin|
// +------------+

// or by field name
Dataset<String> teenagerNamesByFieldDF = teenagersDF.map(new MapFunction<Row, String>() {
  @Override
  public String call(Row row) throws Exception {
    return "Name: " + row.<String>getAs("name");
  }
}, stringEncoder);
teenagerNamesByFieldDF.show();
// +------------+
// |       value|
// +------------+
// |Name: Justin|
// +------------+
```

**编程接口**

​	当JavaBean不能够被事先知道（比如：记录的结构是一个编码的字符串，或者是一个被转换的文本数据集），一个Dataset\<Row>可以通过以下三步来创建：

- 从源RDD创建一个Row类型的RDD
- 创建一个匹配第一步创建的Row结构的代表schema的StructType实例
- 通过SparkSession的createDataFrame方法，将StructType代表的schema运用到第一步的RDD上

例子：

```java
import java.util.ArrayList;
import java.util.List;

import org.apache.spark.api.java.JavaRDD;
import org.apache.spark.api.java.function.Function;

import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;

import org.apache.spark.sql.types.DataTypes;
import org.apache.spark.sql.types.StructField;
import org.apache.spark.sql.types.StructType;

// 源RDD
// Create an RDD
JavaRDD<String> peopleRDD = spark.sparkContext()
  .textFile("examples/src/main/resources/people.txt", 1)
  .toJavaRDD();

// 待创建的schema
// The schema is encoded in a string
String schemaString = "name age";

// Generate the schema based on the string of schema
List<StructField> fields = new ArrayList<>();
for (String fieldName : schemaString.split(" ")) {
  StructField field = DataTypes.createStructField(fieldName, DataTypes.StringType, true);
  fields.add(field);
}
// 创建schema
StructType schema = DataTypes.createStructType(fields);

// 将源RDD转换为Row类型的RDD
// Convert records of the RDD (people) to Rows
JavaRDD<Row> rowRDD = peopleRDD.map(new Function<String, Row>() {
  @Override
  public Row call(String record) throws Exception {
    String[] attributes = record.split(",");
    return RowFactory.create(attributes[0], attributes[1].trim());
  }
});

// 将schema运用的Row类型的RDD上
// Apply the schema to the RDD
Dataset<Row> peopleDataFrame = spark.createDataFrame(rowRDD, schema);

// Creates a temporary view using the DataFrame
peopleDataFrame.createOrReplaceTempView("people");

// SQL can be run over a temporary view created using DataFrames
Dataset<Row> results = spark.sql("SELECT name FROM people");

// The results of SQL queries are DataFrames and support all the normal RDD operations
// The columns of a row in the result can be accessed by field index or by field name
Dataset<String> namesDS = results.map(new MapFunction<Row, String>() {
  @Override
  public String call(Row row) throws Exception {
    return "Name: " + row.getString(0);
  }
}, Encoders.STRING());
namesDS.show();
// +-------------+
// |        value|
// +-------------+
// |Name: Michael|
// |   Name: Andy|
// | Name: Justin|
// +-------------+
```

### 6. 数据源

​	Spark SQL通过DataFrame这个抽象可以操作不同类型的数据源。一个DataFrame可以通过关系型转换操作，也可以被用来创建临时视图。将一个DataFrame注册为一个临时视图之后可以允许我们运行SQL查询。

#### 6.1 数据加载和保存

```java
Dataset<Row> usersDF = spark.read().load("examples/src/main/resources/users.parquet");
usersDF.select("name", "favorite_color").write().save("namesAndFavColors.parquet");
```

​	最简单的方式，默认的数据源（parquet，可以通过spark.sql.sources.default配置）将会被用于所有的操作。

```java
Dataset<Row> peopleDF =
  spark.read().format("json").load("examples/src/main/resources/people.json");
peopleDF.select("name", "age").write().format("parquet").save("namesAndAges.parquet");
```

​	手动设置数据源选项。数据源通过他们的全限定名来指定，内置的数据源可以使用简写（json，parquet，jdbc）。从任何一个数据源加载的DataFrame可以被转换为另一种格式的数据源。

```java
Dataset<Row> sqlDF =
  spark.sql("SELECT * FROM parquet.`examples/src/main/resources/users.parquet`");
```

​	直接在文件上运行SQL。

#### 6.2 保存模式

​	保存操作可以选择一种存储模式，存储模式指定了如果目标数据源已经存在的时候该怎么处理。

| Scala/Java                      | Any Language     | 解释                                       |
| :------------------------------ | ---------------- | ---------------------------------------- |
| SaveMode.ErrorIfExists(default) | "error"(default) | 当保存一个DataFrame到一个数据源时，如果数据已经存在，将会抛出一个异常。 |
| SaveMode.Append                 | "append"         | 添加在存在数据的后面                               |
| SaveMode.Overwrite              | "overwrite"      | 删除存在的数据再写入                               |
| SaveMode.Ignore                 | "ignore"         | 不会改变存在的数据，类似sql里面的：create table if not exists。 |

### 7. Parquet文件

​	Parquet是一种列式存储结构，关于Parquet的存储结构也是一个专门的话题，想了解的同学请自行谷歌。Spark SQL提供了针对Parquet文件的读写支持。

示例：

```java
import org.apache.spark.api.java.function.MapFunction;
import org.apache.spark.sql.Encoders;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;

Dataset<Row> peopleDF = spark.read().json("examples/src/main/resources/people.json");

// DataFrames can be saved as Parquet files, maintaining the schema information
peopleDF.write().parquet("people.parquet");

// Read in the Parquet file created above.
// Parquet files are self-describing so the schema is preserved
// The result of loading a parquet file is also a DataFrame
Dataset<Row> parquetFileDF = spark.read().parquet("people.parquet");

// Parquet files can also be used to create a temporary view and then used in SQL statements
parquetFileDF.createOrReplaceTempView("parquetFile");
Dataset<Row> namesDF = spark.sql("SELECT name FROM parquetFile WHERE age BETWEEN 13 AND 19");
Dataset<String> namesDS = namesDF.map(new MapFunction<Row, String>() {
  public String call(Row row) {
    return "Name: " + row.getString(0);
  }
}, Encoders.STRING());
namesDS.show();
// +------------+
// |       value|
// +------------+
// |Name: Justin|
// +------------+
```

### 8. 表分区发现

​	表分区在系统中是一种常见的优化方式，比如Hive中就使用表分区。在分区表中，数据根据分区列的值被存放在不同的表分区目录里面。Parquet数据源现在支持自动发现的检测分区信息。这是什么意思呢？举个例子，比如下面例子中的用户数据，我们根据用户的性别和国家将用户数据存放在不同的目录结构中，如下：

```
path
└── to
    └── table
        ├── gender=male
        │   ├── ...
        │   │
        │   ├── country=US
        │   │   └── data.parquet
        │   ├── country=CN
        │   │   └── data.parquet
        │   └── ...
        └── gender=female
            ├── ...
            │
            ├── country=US
            │   └── data.parquet
            ├── country=CN
            │   └── data.parquet
            └── ...
```

​	将path/to/table作为参数传递给SparkSession.read().parquet()或者是SparkSession.read().load()方法，Spark SQL将会自动从path/to/table路径中抽取出分区信息，如此操作返回的Dataframe的模式信息为：

```
root
|-- name: string (nullable = true)
|-- age: long (nullable = true)
|-- gender: string (nullable = true)  // 分区列
|-- country: string (nullable = true) // 分区列
```

### 9. JSON Dataset

​	Spark SQL可以自动从一个JSON数据集推测出模式信息并将json数据加载为Dataset\<Row>，这个转换可以通过SparkSession.read().json()来完成。

​	需要注意的是：json文件的内容格式，json文件的内容，一个完整的对象必须写在一行里面，而不能通过格式化的方式来生成一个标准的json文件，否则会转换失败。

```java
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;

// A JSON dataset is pointed to by path.
// The path can be either a single text file or a directory storing text files
Dataset<Row> people = spark.read().json("examples/src/main/resources/people.json");

// The inferred schema can be visualized using the printSchema() method
people.printSchema();
// root
//  |-- age: long (nullable = true)
//  |-- name: string (nullable = true)

// Creates a temporary view using the DataFrame
people.createOrReplaceTempView("people");

// SQL statements can be run by using the sql methods provided by spark
Dataset<Row> namesDF = spark.sql("SELECT name FROM people WHERE age BETWEEN 13 AND 19");
namesDF.show();
// +------+
// |  name|
// +------+
// |Justin|
// +------+
```

### 10. Hive Tables

​	Spark SQL也支持从Hive读写数据。要支持hive，必须要添加额外的配置文件，将hive-site.xml，core-site.xml和hdfs-site.xml文件放置到conf目录下。

```java
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import org.apache.spark.api.java.function.MapFunction;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Encoders;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.SparkSession;

public static class Record implements Serializable {
  private int key;
  private String value;

  public int getKey() {
    return key;
  }

  public void setKey(int key) {
    this.key = key;
  }

  public String getValue() {
    return value;
  }

  public void setValue(String value) {
    this.value = value;
  }
}

// warehouseLocation points to the default location for managed databases and tables
String warehouseLocation = "file:" + System.getProperty("user.dir") + "spark-warehouse";
SparkSession spark = SparkSession
  .builder()
  .appName("Java Spark Hive Example")
  .config("spark.sql.warehouse.dir", warehouseLocation)
  .enableHiveSupport()
  .getOrCreate();

spark.sql("CREATE TABLE IF NOT EXISTS src (key INT, value STRING)");
spark.sql("LOAD DATA LOCAL INPATH 'examples/src/main/resources/kv1.txt' INTO TABLE src");

// Queries are expressed in HiveQL
spark.sql("SELECT * FROM src").show();
// +---+-------+
// |key|  value|
// +---+-------+
// |238|val_238|
// | 86| val_86|
// |311|val_311|
// ...

// Aggregation queries are also supported.
spark.sql("SELECT COUNT(*) FROM src").show();
// +--------+
// |count(1)|
// +--------+
// |    500 |
// +--------+

// The results of SQL queries are themselves DataFrames and support all normal functions.
Dataset<Row> sqlDF = spark.sql("SELECT key, value FROM src WHERE key < 10 ORDER BY key");

// The items in DaraFrames are of type Row, which lets you to access each column by ordinal.
Dataset<String> stringsDS = sqlDF.map(new MapFunction<Row, String>() {
  @Override
  public String call(Row row) throws Exception {
    return "Key: " + row.get(0) + ", Value: " + row.get(1);
  }
}, Encoders.STRING());
stringsDS.show();
// +--------------------+
// |               value|
// +--------------------+
// |Key: 0, Value: val_0|
// |Key: 0, Value: val_0|
// |Key: 0, Value: val_0|
// ...

// You can also use DataFrames to create temporary views within a SparkSession.
List<Record> records = new ArrayList<>();
for (int key = 1; key < 100; key++) {
  Record record = new Record();
  record.setKey(key);
  record.setValue("val_" + key);
  records.add(record);
}
Dataset<Row> recordsDF = spark.createDataFrame(records, Record.class);
recordsDF.createOrReplaceTempView("records");

// Queries can then join DataFrames data with data stored in Hive.
spark.sql("SELECT * FROM records r JOIN src s ON r.key = s.key").show();
// +---+------+---+------+
// |key| value|key| value|
// +---+------+---+------+
// |  2| val_2|  2| val_2|
// |  2| val_2|  2| val_2|
// |  4| val_4|  4| val_4|
// ...
```

