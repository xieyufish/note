## Hibernate---分表实现方式

分表：随着应用程序使用时间的增长，数据存储数据会越来越庞大，单表数据超过百万、千万级，在这种情况下，用户的读写就会变得十分缓慢。为了提升用户体验，如何将交互时间加快就变得十分重要。在这样的情况下，对庞大的数据表进行拆分就不失为一种快速的解决方法。

项目中数据访问层用的是hibernate，那么在hibernate中如何让hibernate自动进行分表呢。这两天查看了大量的网站，自己也做了一些相应的测试。

下面我将介绍三种hibernate分表的方式：

### 命名策略

很多网友都说用hibernate中携带的命名策略方式可以进行愉快的分表操作，我测试之后确实可以进行分表操作。那么如何操作呢？代码如下：

首先我们要定义自己的命名策略方式，我们可以选择实现NamingStrategy接口(要实现10个方法)，或者继承DefaultNamingStrategy类，如下：

```java
public class MyNamingStrategy extends DefaultNamingStrategy {
 /**
  *
  */
 private static final long serialVersionUID = -7881656635681313619L;
 public static final MyNamingStrategy INSTANCE = new MyNamingStrategy();

 @Override
 public String classToTableName(String className) {
  return "TBL_" + StringHelper.unqualify(className) + "_" + Calendar.getInstance().get(Calendar.DAY_OF_MONTH);
 }

}
```

在classToTableName方法中，我们就可以实现自己的命名分表策略。

那么如何让hibernate使用我们自己的命名策略呢？那就是在加载配置文件，初始化SessionFactory的时候：

```java
Configuration configuration = new Configuration().setNamingStrategy(MyNamingStrategy.INSTANCE).configure();
StandardServiceRegistry ssr = new StandardServiceRegistryBuilder()
     .applySettings(configuration.getProperties()).build();
```

以这种方式加载配置文件的时候，在我们的映射文件的class元素就不能设置table属性，否则命名策略将会被table属性覆盖掉。

同时以这种方式实现分表的时候，也不能实现逻辑复杂的分表策略，很适合基于时间的分表策略。

**存在的问题：**

从实现代码的方式我们也可以看出来，命名策略的方式只有在加载配置文件的时候才会被设置，也就是说只在我们的应用加载的时加载hibernate时才会应用命名策略，一旦应用加载完毕，那么我们设置的命名策略就失去了效果，相当于这里的分表只在应用加载的时候分一次。同时由于加载时是加载的分表，如果在HQL或者其他非native sql执行操作时，都值针对的此次加载的分表执行的操作，如果想用HQL或者其他非native sql方式访问之前的分表，那么将不会实现，因为SessionFactory中没有加载进来；只能通过native sql来实现访问其他分表。

如果要实现应用加载后连续分表，那么我们可以设置定时重新加载hibernate配置文件的方式来实现，而不需要重启应用。

### 拦截器

hibernate提供了拦截器类来让我们可以在hibernate执行真正的sql语句之前，对sql语句进行拦截，这就提供给了我们更改执行的sql语句的机会。用拦截器其实是不能实现hibernate的自动分表的，而只能让我们对分表操作；也就是说我们自己要事先分好表，然后在拦截器中操作我们的分表。至于如何事先分表，可以考录数据库存储过程。

那么拦截器如何操作我们的分表呢？

a. 实现我们自己的Interceptor或者集成hibernate已有的Interceptor（EmptyInterceptor）

```java
public class MyInterceptor extends EmptyInterceptor {
 private String name;
 private String tableName;
 
 public MyInterceptor(String tableName, String suffix) {
  this.tableName = tableName;
  this.name = suffix;
 }

 /**
  * @param name
  *            the name to set
  */
 public void setName(String name) {
  this.name = name;
 }

 @Override
 public String onPrepareStatement(String sql) {
  sql = sql.replace(tableName, tableName + "_" + name);
  return super.onPrepareStatement(sql);
 }
}
```

在我们自己的实现类中，我们主要是要重写onPrepareStatement方法，这个方法就是返回我们最终要执行的sql语句的方法。在这个方法中我们可以对sql进行解析替换，然后返回我们自己想要的sql语句。

b. 调用拦截器

```java
@Test
 public void test01() {
  SessionFactory sessionFactory = HibernateUtil.getSessionFactory();
  String tableName = HibernateUtil.getTableName(Person.class);
  Session session = sessionFactory.withOptions().interceptor(new MyInterceptor(tableName, "01")).openSession(); //可以配置多个Interceptor，多表操作相对方便
  Person person = new Person();
  person.setFirstName("hh");
  person.setLastName("aaa");
 
  Transaction transaction = session.beginTransaction();
  session.save(person);
  transaction.commit();
 }
```

我们可以看到拦截器是基于Session的，而不是SessionFactory级别，所以针对插入查询都很灵活。我们想要查询哪个分表，我们就可以通过拦截器构造方法传入我们想要操作的分表即可。

**问题：**

分表操作要自己实现；多表联合操作的时候不太灵活，没验证过，只是我的想法。

### 原生sql(Native SQL)

最后一种分表方式那就是直接使用原生sql语句，创建、插入、查询。hibernate支持原生sql语句的操作，所以用这种方式最直接也是最完全的，不过前提是开发者对sql要有一定的熟悉度。