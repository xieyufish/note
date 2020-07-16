## dependencies(依赖)

### 依赖注入

1. 基于构造函数的注入方式；
2. 基于setter方法的注入方式；
3. 依赖注入时，有依赖关系的bean之间的处理解决过程是怎样的顺序：在容器启动时，会先初始化scope=singleton的bean，如果scope！=singleton将不会在容器启动时加载和初始化bean，而是在第一次被请求时才会初始化一个bean，如果bean被设置为延迟加载模式，那么不管是不是singleton的，都不会再容器启动时加载，而会延迟到第一次请求时；bean之间有依赖关系（也就是说某个bean引用了另一个bean）时，或者拥有depends-on的关系时，被依赖的bean将先被加载，不管被依赖的bean是被设置了延迟加载还是scope！=singleton的；

### 依赖注入属性参数

1. 直接在配置文件中赋值：主要针对原生数据类型、字符串
2. 引用其他bean：通过\<ref bean=""/>子元素或者直接通过ref属性值
3. 内部bean：inner bean一般不必赋予id值，因为只是一个供外部bean使用而已
4. 集合的配置：List，Map，Properties，Set对应的有\<list>,\<map>\<props>\<set>元素
5. null或者空字符串值：null有专门的<null/>,空字符串直接就是“”
6. p-namespace的使用：一般我们给属性设置值的时候，是通过\<property>元素来设置的，但如果我们不想通过这种方式，而是直接在bean元素的属性里面给bean的属性设值应该怎么做呢？我们可以在配置文件中引入p的命名空间，通过给bean摄者p:属性名=""这种属性的方式来给bean的属性赋值
7. c-namespace的使用：跟p-namespace使用方式一样,不过是针对构造注入的一种方式
8. 组合类属性的设置：通过点号（.）来设置，比如A类包含B类对象b，B类有属性aa，那么在对A实例化时，可以通过b.aa方式给B类的aa属性设置,不过这种方式赋值的前提是A中的b属性不能为空,否则会报空指针错误