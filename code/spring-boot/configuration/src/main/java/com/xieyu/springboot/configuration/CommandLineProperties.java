package com.xieyu.springboot.configuration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;

/**
 * <b>类作用描述：</b>
 * <pre>
 * 命令行属性：
 * SpringApplication会把命令行中以--开头的选项参数转换为属性，并加到Environment实例中
 * 命令行设置的属性值会覆盖application.properties文件中定义的属性值
 * </pre>
 * <b>创建者：</b>  	xieyu <br>
 * <b>所属项目：</b>	configuration <br>
 * <b>创建日期：</b>	2019年03月15日 19:27:42 <br>
 * <b>修订记录：</b>	<br>
 * <b>当前版本：</b>	1.0.0 <br>
 * <b>参考：</b>		
 */
@SpringBootApplication
public class CommandLineProperties {
	
	@Value("${my.secret}")
	private String secret;
	
	public static void main(String[] args) {
		SpringApplication app = new SpringApplication(CommandLineProperties.class);
//		app.setAddCommandLineProperties(false); // 默认值是true，禁止将命令行的选项参数加入到Environment中，可以注解掉这一行查看效果
		ApplicationContext context = app.run(args);
		CommandLineProperties clp = context.getBean(CommandLineProperties.class);
		System.out.println(clp.secret);
	}
}
