/**
 * <b>类作用描述：</b>
 * <pre>
 * springboot的外部配置：
 * 我们可以通过properties文件、YAML文件、环境变量和命令行参数来配置运行环境；
 * 可以直接通过@Value注解来引用这些配置值，属性配置优先级如下（上面的会覆盖下面的）：
 * - 用户home目录下的.spring-boot-devtools.properties文件；
 * - 测试类上的@TestPropertySource注解
 * - @SpringBootTest注解上的
 * - 命令行上的参数
 * - SPRING_APPLICATION_JSON的系统属性或者环境变量
 * - ServletConfig的初始化参数值
 * - ServletContext的初始化参数值
 * - 在java:comp/env路径下的jndi属性
 * - java的系统属性（System.getProperties()）
 * - 操作系统环境变量
 * - RandomValuePropertySource里面的属性值，对应于配置文件中的random.*生成的值
 * - 指定的profile环境下，jar包外部的配置文件，例如：application-{profile}.properties或者是application-{profile}.yaml
 * - 指定的profile环境下，jar包内部的配置文件
 * - jar包外部的application.properties文件
 * - jar包内部的application.properties
 * - 配置在@Configuration类上的@PropertySource指定的配置文件
 * - 默认的属性值，通过SpringApplication.setDefaultProperties设定的值
 * </pre>
 * <b>创建者：</b>  	xieyu <br>
 * <b>所属项目：</b>	configuration <br>
 * <b>创建日期：</b>	2019年03月15日 18:59:09 <br>
 * <b>修订记录：</b>	<br>
 * <b>当前版本：</b>	1.0.0 <br>
 * <b>参考：</b>		
 */
package com.xieyu.springboot.configuration;