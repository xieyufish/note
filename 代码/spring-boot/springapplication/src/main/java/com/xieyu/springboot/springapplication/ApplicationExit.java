package com.xieyu.springboot.springapplication;

import org.springframework.boot.ExitCodeGenerator;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

/**
 * <b>类作用描述：</b>
 * <pre>
 * 应用关闭
 * SpringApplication应用会在jvm上注册一个关闭hook函数，使得Spring应用上下文可以优雅的关闭退出；任何Spring的生命周期回调函数都会被调用
 * 比如：DisposableBean，@PreDestroy等相应方法都会被调用到；
 * 除此之外，还可以通过实现ExitCodeGenerator接口来返回一个特点的关闭状态码
 * </pre>
 * <b>创建者：</b>  	xieyu <br>
 * <b>所属项目：</b>	springapplication <br>
 * <b>创建日期：</b>	2019年03月15日 17:34:05 <br>
 * <b>修订记录：</b>	<br>
 * <b>当前版本：</b>	1.0.0 <br>
 * <b>参考：</b>		
 */
@SpringBootApplication
public class ApplicationExit {
	
	@Bean
	public ExitCodeGenerator exitCodeGenerator() {
		return () -> 42;
	}
	
	public static void main(String[] args) {
		System.exit(SpringApplication.exit(SpringApplication.run(ApplicationExit.class, args)));
	}
}
