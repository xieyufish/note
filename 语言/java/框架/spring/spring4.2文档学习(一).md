## container overview(容器预览)

### 配置元数据

spring配置基本信息的方式有三种：

1. 基于Annotation（spring自有的注解）的配置方式；
2. 基于Java（也是注解，不过是Java标准中的注解）的配置方式；
3. 基于xml的配置方式；

其中第三种是传统的，也是最简单明了、一目了然的配置方式，如下例：

```xml
<?xml version="1.0" encoding="UTF-8"?>
<beans xmlns="http://www.springframework.org/schema/beans"xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"xsi:schemaLocation="http://www.springframework.org/schema/beans
        http://www.springframework.org/schema/beans/spring-beans.xsd">
  	<bean id="..."class="..."><!-- collaborators and configuration for this bean go here -->		</bean>
  	<beanid="..."class="..."><!-- collaborators and configuration for this bean go here -->		
    </bean><!-- more bean definitions go here -->
</beans>
```

### 容器初始化

初始化容器是很直观的，通过定位到配置的文件的路径，将路径传递给ApplicationContext的构造方法就会加载配置文件信息初始化容器，例如：

```java
ApplicationContext context = new ClassPathXmlApplicationContext(""); //通过xml方式的配置
```

上面是基于classpath类路径来加载配置文件，还有一个实现是基于文件系统来加载配置文件的方式，实现类是：FileSystemXmlApplicationContext，还有其他的通过注解的方式等各种ApplicationContext加载方式。

如果系统的spring配置文件时以模块划分的，也就是说系统存在多个spring配置文件，那么要怎么将不同模块的配置内容加载到同一个spring容器中呢？

1. ClassPathXmlApplicationContext和FileSystemXMLApplicationContext类都提供了字符串数组参数的构造方法，所以，如果多个模块的不同配置文件，可以将他们的路径串组成为一个字符串数组传递给构造方法即可实现多个配置文件加载在同一个容器中；

2. 在一个主配置文件中，同过引入方式，将多个模块的配置文件组成一个逻辑配置文件

   ```xml
   <beans>
     <import resource="services.xml"/>
     <import resource="resources/messageSource.xml"/>							
     <importresource="/resources/themeSource.xml"/>
     <bean id="bean1"class="..."/>
     <bean id="bean2"class="..."/>
   </beans>
   ```

### 容器的使用

```java
// create and configure beans
ApplicationContext context =
    new ClassPathXmlApplicationContext(new String[] {"services.xml", "daos.xml"});

// retrieve configured instance
PetStoreService service = context.getBean("petStore", PetStoreService.class);

// use configured instance
List<String> userList = service.getUsernameList();
```

