## API文档工具-Swagger的集成

[TOC]

### 1. 介绍

​    Swagger是一个简单又强大的能为你的Restful风格的Api生成文档工具。在项目中集成这个工具，根据我们自己的配置信息能够自动为我们生成一个api文档展示页，可以在浏览器中直接访问查看项目中的接口信息，同时也可以测试每个api接口。Swagger生成的api文档是实时更新的，你写的api接口有任何的改动都会在文档中及时的表现出来。下面我将详细介绍在项目中如何集成Swagger。

### 2. 项目环境

​    jdk1.7，Spring4.1.2，Mybatis3

​    Spring提供了一个与Swagger的集成工具包springfox，让我们的Spring项目能够更好的与Swagger融合，下面是我的项目环境详细信息。

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
	xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/maven-v4_0_0.xsd">
	<modelVersion>4.0.0</modelVersion>
	<groupId>com.shell</groupId>
	<artifactId>shiro</artifactId>
	<packaging>war</packaging>
	<version>0.0.1-SNAPSHOT</version>
	<name>shiro Maven Webapp</name>
	<url>http://maven.apache.org</url>

	<properties>
		<spring.version>4.1.2.RELEASE</spring.version>
		<slf4j.version>1.6.6</slf4j.version>
		<log4j.version>1.2.17</log4j.version>
		<mysql.version>5.1.29</mysql.version>
		<mybatis.version>3.2.7</mybatis.version>
		<project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
	</properties>
	<dependencies>
		<dependency>
			<groupId>junit</groupId>
			<artifactId>junit</artifactId>
			<version>3.8.1</version>
			<scope>test</scope>
		</dependency>

		<!-- spring核心 -->
		<!-- springframe start -->
		<dependency>
			<groupId>org.springframework</groupId>
			<artifactId>spring-core</artifactId>
			<version>${spring.version}</version>
		</dependency>

		<dependency>
			<groupId>org.springframework</groupId>
			<artifactId>spring-context-support</artifactId>
			<version>${spring.version}</version>
		</dependency>

		<dependency>
			<groupId>org.springframework</groupId>
			<artifactId>spring-web</artifactId>
			<version>${spring.version}</version>
		</dependency>

		<dependency>
			<groupId>org.springframework</groupId>
			<artifactId>spring-oxm</artifactId>
			<version>${spring.version}</version>
		</dependency>

		<dependency>
			<groupId>org.springframework</groupId>
			<artifactId>spring-tx</artifactId>
			<version>${spring.version}</version>
		</dependency>

		<dependency>
			<groupId>org.springframework</groupId>
			<artifactId>spring-jdbc</artifactId>
			<version>${spring.version}</version>
		</dependency>
		<dependency>
			<groupId>org.springframework</groupId>
			<artifactId>spring-webmvc</artifactId>
			<version>${spring.version}</version>
		</dependency>

		<dependency>
			<groupId>org.springframework</groupId>
			<artifactId>spring-aop</artifactId>
			<version>${spring.version}</version>
		</dependency>

		<dependency>
			<groupId>org.springframework</groupId>
			<artifactId>spring-test</artifactId>
			<version>${spring.version}</version>
		</dependency>
		<!-- springframe end -->
		
		<dependency>
			<groupId>org.aspectj</groupId>
			<artifactId>aspectjweaver</artifactId>
			<version>1.8.2</version>
		</dependency>

		<!-- 日志文件管理 -->
		<!-- log start -->
		<dependency>
			<groupId>log4j</groupId>
			<artifactId>log4j</artifactId>
			<version>${log4j.version}</version>
		</dependency>
		<dependency>
			<groupId>org.slf4j</groupId>
			<artifactId>slf4j-api</artifactId>
			<version>${slf4j.version}</version>
		</dependency>
		<dependency>
			<groupId>org.slf4j</groupId>
			<artifactId>slf4j-log4j12</artifactId>
			<version>${slf4j.version}</version>
		</dependency>
		<!-- log end -->
		
		<dependency>
			<groupId>mysql</groupId>
			<artifactId>mysql-connector-java</artifactId>
			<version>${mysql.version}</version>
		</dependency>
		<!-- mybatis核心 -->
		<dependency>
			<groupId>org.mybatis</groupId>
			<artifactId>mybatis</artifactId>
			<version>${mybatis.version}</version>
		</dependency>
		<!-- mybatis/spring -->
		<dependency>
			<groupId>org.mybatis</groupId>
			<artifactId>mybatis-spring</artifactId>
			<version>1.2.2</version>
		</dependency>
		<!-- 阿里巴巴数据源包 -->
		<dependency>
			<groupId>com.alibaba</groupId>
			<artifactId>druid</artifactId>
			<version>1.0.2</version>
		</dependency>
		
		<dependency>
			<groupId>javax.servlet</groupId>
			<artifactId>javax.servlet-api</artifactId>
			<version>3.1.0</version>
		</dependency>
		
		<dependency>
			<groupId>com.alibaba</groupId>
			<artifactId>fastjson</artifactId>
			<version>1.2.7</version>
		</dependency>
		
		<!-- Swagger api文档生成工具依赖包 -->
		<dependency>
            <groupId>io.springfox</groupId>
            <artifactId>springfox-swagger2</artifactId>
            <version>2.2.2</version>
        </dependency>
        <dependency>
			<groupId>com.fasterxml.jackson.core</groupId>
			<artifactId>jackson-core</artifactId>
			<version>2.2.3</version>
		</dependency>
		<dependency>
			<groupId>com.fasterxml.jackson.core</groupId>
			<artifactId>jackson-databind</artifactId>
			<version>2.2.3</version>
		</dependency>
		<dependency>
			<groupId>com.fasterxml.jackson.core</groupId>
			<artifactId>jackson-annotations</artifactId>
			<version>2.2.3</version>
		</dependency>
        <!-- Swagger end -->
		
	</dependencies>
	<build>
		<finalName>shiro</finalName>
		<pluginManagement>
			<plugins>
				<plugin>
					<groupId>org.apache.maven.plugins</groupId>
					<artifactId>maven-compiler-plugin</artifactId>
					<configuration>
						<source>1.7</source>
						<target>1.7</target>
					</configuration>
				</plugin>
			</plugins>
		</pluginManagement>
	</build>
</project>
```

### 3. Swagger配置步骤

#### 3.1 自定义配置类实现

​    Swagger会根据这个配置类的具体实现来生成我们相应的api文档，比如：过滤指定接口

```java
package com.shell.swagger;

import org.springframework.context.annotation.Bean;

import springfox.documentation.service.ApiInfo;
import springfox.documentation.spi.DocumentationType;
import springfox.documentation.spring.web.plugins.Docket;
import springfox.documentation.swagger2.annotations.EnableSwagger2;

/**
 * @author Administrator
 */
@EnableSwagger2
public class CustomSwaggerConfig {
	@Bean
	public Docket myDocket() {
		Docket docket = new Docket(DocumentationType.SWAGGER_2);
		ApiInfo apiInfo = new ApiInfo("基础框架", "这是一个项目的基础框架结构，构建新项目可以在这个基础上搭建","1.0","apiDocs","1536999495@qq.com","","");
		docket.apiInfo(apiInfo);
		return docket;
	}
}
```

#### 3.2 将上面的配置类注入到Spring中

```xml
<?xml version="1.0" encoding="UTF-8"?>
<beans xmlns="http://www.springframework.org/schema/beans"
	xmlns:mvc="http://www.springframework.org/schema/mvc" xmlns:context="http://www.springframework.org/schema/context"
	xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
	xmlns:aop="http://www.springframework.org/schema/aop"
	xsi:schemaLocation="http://www.springframework.org/schema/beans  
            http://www.springframework.org/schema/beans/spring-beans-3.1.xsd 
            http://www.springframework.org/schema/aop  
      		http://www.springframework.org/schema/aop/spring-aop-3.2.xsd 
            http://www.springframework.org/schema/context   
            http://www.springframework.org/schema/context/spring-context-3.1.xsd  
            http://www.springframework.org/schema/mvc  
            http://www.springframework.org/schema/mvc/spring-mvc-3.1.xsd">
	<context:component-scan base-package="com.shell"></context:component-scan>
	<aop:aspectj-autoproxy></aop:aspectj-autoproxy>
	<!-- 视图 -->
	<bean
		class="org.springframework.web.servlet.view.InternalResourceViewResolver">
		<property name="prefix" value="/WEB-INF/views/" />
		<property name="suffix" value=".jsp" />
	</bean>
	
	<mvc:annotation-driven></mvc:annotation-driven>
	<mvc:resources location="/WEB-INF/static/" mapping="/static/**" />
	<mvc:resources location="/WEB-INF/static/swagger/" mapping="/swagger/**"/>
	
    <!-- 将我们在上面实现的类在这里声明 -->
	<bean class="com.shell.swagger.CustomSwaggerConfig"></bean>
</beans>
```

**注意:** *自定义类CustomSwaggerConfig这个类的bean定义放置在spring的哪个配置很重要,我这里定义的自动扫描包路径也是在这个配置文件中所以才将CustomSwaggerConfig放在这个类中.如果你的spring和springmvc分了两个配置文件,就要注意CustomSwaggerConfig的放置位置*

#### 3.3 api接口注解

​    

```java
package com.shell.basemodel;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import com.shell.basemodel.ifc.BaseModelService;
import com.shell.common.controller.BaseController;
import com.shell.common.model.ResultObject;

import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;

/**
 * @author shell
 */
@Controller
@Scope("prototype")
@RequestMapping("/model")
public class BaseModelController extends BaseController {
	
	@Autowired
	private BaseModelService service;
	
	@RequestMapping("/test/{hhh}")
	@ApiOperation(notes = "测试Swagger", value = "test")
	public ResultObject test(@ApiParam(required = true, name="输出") @PathVariable("hhh") String hhh ) {
		System.out.println(hhh);
		System.out.println(request.getParameter("name"));
		BaseModel t = new BaseModel();
		t.setId(2334l);
		t.setTt(34.53456145352548f);
//		service.test();
		Object obj = service.add(t);
		ResultObject resultObject = new ResultObject();
		resultObject.setData(obj);
		return resultObject;
	}
}
```

**springfox默认会将所有的接口都给你生成文档,不管你有没有使用注解@ApiOperation这个注解注释接口方法,并且如果你没有为你的接口指定访问方式,他也会为这个接口生成所有访问方式的文档, 下面会有结构展示图.**

#### 3.4 修改swagger中index.html内容

​    要在浏览器中访问查看我们的文档,那么就必需要下载Swagger-UI这个ui组件, 将dist目录中的内容加入到项目中。然后修改index.html文件中的内容。

```html
<script type="text/javascript">
    $(function () {
      var url = window.location.search.match(/url=([^&]+)/);
      if (url && url.length > 1) {
        url = decodeURIComponent(url[1]);
      } else {
        // 修改这个url地址为: http://{ip:port}/{projectname}/v2/api-docs
        // {ip:port}: 改为你自己的项目地址, {projectname}:改为你自己的项目访问路径
        // v2/api-docs: 固定不变
        url = "http://localhost:8080/shiro/v2/api-docs";  
      }

      hljs.configure({
        highlightSizeThreshold: 5000
      });

      // Pre load translate...
      if(window.SwaggerTranslator) {
        window.SwaggerTranslator.translate();
      }
      window.swaggerUi = new SwaggerUi({
        url: url,
        dom_id: "swagger-ui-container",
        supportedSubmitMethods: ['get', 'post', 'put', 'delete', 'patch'],
        onComplete: function(swaggerApi, swaggerUi){
          if(typeof initOAuth == "function") {
            initOAuth({
              clientId: "your-client-id",
              clientSecret: "your-client-secret-if-required",
              realm: "your-realms",
              appName: "your-app-name",
              scopeSeparator: " ",
              additionalQueryStringParams: {}
            });
          }

          if(window.SwaggerTranslator) {
            window.SwaggerTranslator.translate();
          }
        },
        onFailure: function(data) {
          log("Unable to Load SwaggerUI");
        },
        docExpansion: "none",
        jsonEditor: false,
        defaultModelRendering: 'schema',
        showRequestHeaders: false
      });

      window.swaggerUi.load();

      function log() {
        if ('console' in window) {
          console.log.apply(console, arguments);
        }
      }
  });
  </script>
```

### 4. 结果展示

![1](images\1.png) 

**BaseModelController中的接口: **

![2](images\2.png) 

**每个接口文档的具体描述**

![3](images\3.png)

​     **从上面的截图我们可以看到, 生成的文档把所有的接口信息都列出来了, 把我们不想让他生成的接口文档也列出来了,比如: IndexController中的接口. 如果我们只想让让列出我们配置的想要的接口的文档应该怎么做呢?我们可以修改我们的CustomSwaggerConfig这个Swagger配置类来过滤甚至生成地址的文档模型.**

修改后的CustomSwaggerConfig如下:

```java
package com.shell.swagger;

import org.springframework.context.annotation.Bean;

import io.swagger.annotations.ApiOperation;
import springfox.documentation.builders.RequestHandlerSelectors;
import springfox.documentation.service.ApiInfo;
import springfox.documentation.spi.DocumentationType;
import springfox.documentation.spring.web.plugins.Docket;
import springfox.documentation.swagger2.annotations.EnableSwagger2;

/**
 * @author Administrator
 *
 */
@EnableSwagger2
public class CustomSwaggerConfig {
	
	@Bean
	public Docket myDocket() {
		
		Docket docket = new Docket(DocumentationType.SWAGGER_2);
		ApiInfo apiInfo = new ApiInfo("基础框架", "这是一个项目的基础框架结构，构建新项目可以在这个基础上搭建","1.0","apiDocs","1536999495@qq.com","","");
		docket.apiInfo(apiInfo);
		
      // 下面这句代码是只生成被ApiOperation这个注解注解过的api接口
      // 以及最后一定要执行build()方法,不然不起作用
		docket.select().apis(RequestHandlerSelectors.withMethodAnnotation(ApiOperation.class)).build();
		return docket;
	}
}
```

修改配置后的结果:

![4](images\4.png)

​    从结果看出: IndexController中的接口没有再生成(因为没有注解ApiOperation)，只生成了test这个被ApiOperation注解了的接口的文档, 但同时我们也看到他依然生成了6中访问方式的接口文档,这是因为我们的test接口定义时没有明确的指定是什么方法访问的。我想这也是为什么Swagger是针对Restful风格的Api文档生成工具的原因吧。

### 5. 结语

​    Swagger这个工具之前一直没用过，之前写api文档都是直接用word或者是其他的编辑工具手写的。这个也是同学用过说有这样的工具。后来发现类似这样的工具有很多，比如：apiblueprint, raml等等。我这里也只是一个简单快速的集成，具体详细的配置并没有深入去研究， 但是我想既然只是作为一个工具，我们只要知道怎么使用，遇到问题和需求再继续研究也不迟。