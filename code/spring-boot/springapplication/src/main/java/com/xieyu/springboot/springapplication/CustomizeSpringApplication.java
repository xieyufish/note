package com.xieyu.springboot.springapplication;

import org.springframework.boot.Banner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * <b>类作用描述：</b>
 * <pre>
 * 如果SpringApplication的默认配置不符合你的使用场景，那么可以创建一个SpringApplication的实例，并且自定义这个实例的属性；
 * 也可以通过配置文件完成自定义
 * 例如：关闭banner打印
 * </pre>
 * <b>创建者：</b>  	xieyu <br>
 * <b>所属项目：</b>	springapplication <br>
 * <b>创建日期：</b>	2019年03月15日 16:29:44 <br>
 * <b>修订记录：</b>	<br>
 * <b>当前版本：</b>	1.0.0 <br>
 * <b>参考：</b>		
 */
@SpringBootApplication
public class CustomizeSpringApplication {
	
	public static void main(String[] args) {
		SpringApplication app = new SpringApplication(CustomizeSpringApplication.class);
		app.setBannerMode(Banner.Mode.OFF);	// 优先级低于配置文件中的配置
		app.run(args);
	}
}
