package com.xieyu.springboot.springapplication;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * <b>类作用描述：</b>
 * <pre>
 * 如果在SpringApplication启动之后，你想执行某些操作，那么你可以实现ApplicationRunner或者是CommandLineRunner;
 * 这两个接口都提供了一个run方法，在SpringApplication.run()执行完成之前调用
 * </pre>
 * <b>创建者：</b>  	xieyu <br>
 * <b>所属项目：</b>	springapplication <br>
 * <b>创建日期：</b>	2019年03月15日 17:18:28 <br>
 * <b>修订记录：</b>	<br>
 * <b>当前版本：</b>	1.0.0 <br>
 * <b>参考：</b>		
 */
@SpringBootApplication
public class Runner {
	public static void main(String[] args) {
		SpringApplication.run(Runner.class, args);
	}
}
