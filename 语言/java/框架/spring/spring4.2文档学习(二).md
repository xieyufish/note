## bean overview(bean的预览)

### Bean命名

通过id或者name属性来标识一个bean的唯一性，name属性值可以使多个值，通过逗号、空格、分号来区分每个值，通知也可以通过alias元素来指定一个bean的别名

### Bean初始化

1. 通过构造方法来初始化一个bean

   ```xml
   <bean id="exampleBean"class="examples.ExampleBean"/>
   <bean name="anotherExample"class="examples.ExampleBeanTwo"/>
   ```

2. 通过静态工厂方法来初始化一个bean

   ```xml
   <beanid="clientService"class="examples.ClientService"factory-method="createInstance"/>
   ```

   ```java
   publicclass ClientService {
       private static ClientService clientService = new ClientService();
       private ClientService() {}

       public static ClientService createInstance() {
           return clientService;
       }
   }
   ```

3. 通过对象工厂方法来初始化一个bean

   ```xml
   <!-- the factory bean, which contains a method called createInstance() -->
   <bean id="serviceLocator"class="examples.DefaultServiceLocator">
     <!-- inject any dependencies required by this locator bean -->
   </bean>
   <!-- the bean to be created via the factory bean -->
   <bean id="clientService" factory-bean="serviceLocator" factory-method="createClientServiceInstance"/>
   ```

   ```java
   publicclass DefaultServiceLocator {

       private static ClientService clientService = new ClientServiceImpl();
       private DefaultServiceLocator() {}

       public ClientService createClientServiceInstance() {
           return clientService;
       }
   }
   ```