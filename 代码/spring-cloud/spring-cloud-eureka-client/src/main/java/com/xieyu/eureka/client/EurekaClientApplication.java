package com.xieyu.eureka.client;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.netflix.eureka.EnableEurekaClient;

/**
 * <b>类作用描述：</b>
 * <pre>
 * 
 * </pre>
 * <b>创建者：</b>  	xieyu <br>
 * <b>所属项目：</b>	spring-cloud-eureka-client <br>
 * <b>创建日期：</b>	2019年03月18日 16:36:23 <br>
 * <b>修订记录：</b>	<br>
 * <b>当前版本：</b>	1.0.0 <br>
 * <b>参考：</b>		
 */
@EnableEurekaClient
@SpringBootApplication
public class EurekaClientApplication {
	
	public static void main(String[] args) {
		SpringApplication.run(EurekaClientApplication.class, args);
	}
}
