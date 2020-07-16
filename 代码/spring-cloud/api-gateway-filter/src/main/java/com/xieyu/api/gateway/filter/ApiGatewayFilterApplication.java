package com.xieyu.api.gateway.filter;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.netflix.zuul.EnableZuulProxy;
import org.springframework.context.annotation.Bean;

/**
 * <b>类作用描述：</b>
 * <pre>
 * </pre>
 * <b>创建者：</b>  	xieyu <br>
 * <b>所属项目：</b>	api-gateway-filter <br>
 * <b>创建日期：</b>	2019年03月20日 22:53:44 <br>
 * <b>修订记录：</b>	<br>
 * <b>当前版本：</b>	1.0.0 <br>
 * <b>参考：</b>		
 */
@EnableZuulProxy
@SpringBootApplication
public class ApiGatewayFilterApplication {
	
	@Bean
	public AccessFilter accessFilter() {
		return new AccessFilter();
	}
	
	public static void main(String[] args) {
		SpringApplication.run(ApiGatewayFilterApplication.class, args);
	}
}
