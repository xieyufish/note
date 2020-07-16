## depends-on属性的使用,lazy-initialize（懒加载）的方式, 自动装配（autowiring）

### depends-on

在spring中，多个bean之间的依赖有多种多样，比如直接通过<ref/>引用依赖，像这种依赖我们称之为直接依赖。但有时，多个bean之间的依赖并不是这么直接的，比如说在数据库访问层，我们就必修要先注册建立连接，才能操作访问数据库，向这种的话就是间接依赖，那么要怎么控制这些bean之间的实例化顺序呢？那就是通过depends-on属性来控制：

```xml
<bean id="beanOne" class="ExampleBean" depends-on="manager"></bean>
<bean id="manager" class="ManagerBean"></bean>
```

有多个依赖时,用逗号隔开：

```xml
<bean id="beanOne" class="ExampleBean" depends-on="manager,accountDao">
	<property name="manager" ref="manager"></property>
</bean>
<bean id="manager" class="ManagerBean"></bean>
<bean id="accountDao" class="x.y.jdbc.JdbcAccountDao"></bean>
```

### lazy-initialize

在spring中，针对每个bean元素配置的类，在容器启动时，默认生命周期（singleton）的bean都会被实例化，（prototype的在每次请求时实例化）。那么如果要想控制一个singleton的bean在spring容器启动时，不被实例化，该怎么做呢？很简单，只需要在每个bean元素中，设置其属性lazy-init="true"即可不被实例化，而只在被请求时才被实例化。那如果我想让所有的bean在容器启动时都不被初始化，难道要每个bean都写一个lazy-init属性么？不用那么麻烦,只需要在beans根元素的default-lazy-init属性设置为true即可。

### autowiring

自动装配的意思就是以前在配置bean的时候，我们都要通过property或者构造方式注入的时候，都要显示的指定值或者\<ref/>引用；如果我们配置了自动装配的话，那么spring将自动根据我们的配置来为我们注入属性。

装配方式：

![1](images\1.png)