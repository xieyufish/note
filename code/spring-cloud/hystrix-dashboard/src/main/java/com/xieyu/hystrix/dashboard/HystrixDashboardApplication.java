package com.xieyu.hystrix.dashboard;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.netflix.hystrix.dashboard.EnableHystrixDashboard;

/**
 * <b>类作用描述：</b>
 * <pre>
 * 
 * </pre>
 * <b>创建者：</b>  	xieyu <br>
 * <b>所属项目：</b>	hystrix-dashboard <br>
 * <b>创建日期：</b>	2019年03月19日 19:41:09 <br>
 * <b>修订记录：</b>	<br>
 * <b>当前版本：</b>	1.0.0 <br>
 * <b>参考：</b>		
 */
@EnableHystrixDashboard
@SpringBootApplication
public class HystrixDashboardApplication {
	public static void main(String[] args) {
		SpringApplication.run(HystrixDashboardApplication.class, args);
	}
}