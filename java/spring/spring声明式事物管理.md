## Spring声明式事物管理，即他的属性Propagation

```xml
<bean id="transactionManager"
      class="org.springframework.jdbc.datasource.DataSourceTransactionManager">
  <property name="dataSource" ref="dataSource" />
</bean>

<tx:advice id="txAdvice" transaction-manager="transactionManager">
  <tx:attributes>
    <tx:method name="add*" read-only="false" propagation="REQUIRED" rollback-for="java.lang.Exception"/>
    <tx:method name="get*" read-only="true"/>
    <tx:method name="delete" read-only="false" rollback-for="java.lang.Exception"/>
    <tx:method name="update*" read-only="false" rollback-for="java.lang.Exception"/>
    <tx:method name="count*" read-only="true"/>
    <tx:method name="*" read-only="false" rollback-for="java.lang.Exception"/>
  </tx:attributes>
</tx:advice>

<aop:config>
  <aop:pointcut expression="execution(* com.goldenculture..*Service.*(..))" id="pointCut"/>
  <aop:advisor advice-ref="txAdvice" pointcut-ref="pointCut"/>
</aop:config>
```

spring使用tx命名空间配置事物3步搞定：

1. 声明一个事物管理器
2. 配置事物属性
3. aop配置切入点和属性管理，也就是要在哪些方法上使用事物

理解事物属性**propagation：**

Spring中提供的propagation值总共有7个，他们分别是：REQUIRED，SUPPORTS，MANDATORY，REQUIRES_NEW，NOT_SUPPORTS，NEVER，NESTED下面分别对这几个属性意思进行讲解：

举例：

A类中拥有方法methodA(),  B类中拥有方法methodB(),此两个方法都属于事物管理层面，且都操作数据库，在methodA()中调用方法methodB(), methodB()会抛出一个runtimeexception

**required：**如果当前不存在事物，则创建一个新的事物，如果已经存在一个事物，那么不创建新事物，并且加入当前事物执行。针对例子，在调用执行A方法时，会创建一个事物，并且创建一个数据库会话；当执行到方法B时，由于已经存在事物，故将方法B直接加入当前A的事物中，并不会再创建一个新的事物，也不会再创建新的数据库会话，而是直接使用A获得的那个会话对象。 由于方法B模拟抛出一个异常，如果方法B不对此异常try-catch而是直接抛出，不管A有没有try-catch,那么就会造成整个事物回滚。 而如果B对此异常try-catch了，那么不管A有没有try-catch,那么造成的结果是A方法对数据库的操作依然不会回滚，而只有方法B会回滚操作；

**supports：**  如果当前存在事物，则不创建新的事物，如果不存在事物，则无事物执行，所以如果配置文件中事物属性propagation的值配置的都是supports值，那么整个应用都将是无事物管理执行；

**mandatory：** 如果存在事物，则不创建新的事物，如果不存在事物，则抛出异常，同supports属性值一样,如果配置文件中事物属性propagation的值配置的都是mandatory,那么整个应用将抛出异常而不能操作数据库；

**requires_new：** 总是开启一个新的事物，如果已经存在事物，则挂起当前事物。事物之间互不影响，并且创建的新事物也会创建新的数据库会话；

**nested：**  会创建一个新的事物，但是是一个内嵌的事物，也就是说是包含在存在的事物里面的一个事物。不会创建新的数据库会话，而是用外层事物的数据库会话对象。外层事物会设置一个savepoint（）。方法B抛出异常没有捕捉时，如果方法A不对异常进行捕捉，那么会造成A方法的操作回滚，而如果方法A对其进行捕捉，那么方法A的操作也不会回滚。内层事物出错时可能影响外层事物（不捕捉错误时），外层事物出错时则不会影响到内层事物的执行；

**not_support：** 总是非事物执行，并挂起任何存在的事物；

**never：** 非事物执行，如果存在事物则抛出异常。

综上，事物总是和一个数据库的会话关联，不过要注意此处的事物并不单纯的是数据库中的一个事物，这里的事物是系列方法过程的总体执行，某个部分的异常（不管是数据库操作的异常，还是代码处理产生的异常）都会引起事物的回滚。同时为了保持数据的一致性，建议不要对一些运行时异常进行捕捉，以免造成不必要的困扰。





