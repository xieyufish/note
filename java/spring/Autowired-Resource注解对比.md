## @Autowired  @Resource @Quilifier

@Autowired和@Resource都可以完成bean属性的自动装配注入

区别：

1. @Resource：拥有两个属性name和type,分别代表的意思是按照名字和类型来装配对象，如果两个属性值都没有指定， 默认是按名称来注入的，如果实在找不到对应的名称则会按照原始类型类装配注入，如果指定name属性值则按名字来查找注入，如果找不到则抛出异常，如果指定type属性值则按类型来查找注入，如果同时指定了两个属性值，则会查找名字和类型都匹配的bean来注入，如果找不到，则会抛出异常

   @Resource：是j2ee标准提供的

2. @Autowired：默认是按照类型来装配注入的，如果想要按照名称来装配注入，那么就要结合@Quilifier注解来指定特定名称装配注入

   @Autowired：是spring提供的


3. @Resource和@Autowired都可以书写标注在字段或者该字段的setter方法之上，写在属性bean上时可以不用写setter方法

如果想减少依赖性，建议使用@Resource注解