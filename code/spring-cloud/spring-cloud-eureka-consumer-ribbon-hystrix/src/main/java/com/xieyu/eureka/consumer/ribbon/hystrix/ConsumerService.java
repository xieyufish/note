package com.xieyu.eureka.consumer.ribbon.hystrix;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import com.netflix.hystrix.contrib.javanica.annotation.HystrixCommand;

/**
 * <b>类作用描述：</b>
 * <pre>
 * 
 * </pre>
 * <b>创建者：</b>  	xieyu <br>
 * <b>所属项目：</b>	spring-cloud-eureka-consumer-ribbon-hystrix <br>
 * <b>创建日期：</b>	2019年03月19日 17:39:17 <br>
 * <b>修订记录：</b>	<br>
 * <b>当前版本：</b>	1.0.0 <br>
 * <b>参考：</b>		
 */
@Component
public class ConsumerService {
	@Autowired
	private RestTemplate restTemplate;
	
	@HystrixCommand(fallbackMethod = "fallback")
	public String consumer() {
		return restTemplate.getForObject("http://eureka-client/dc", String.class);
	}
	
	public String fallback() {
		return "fallback";
	}
}
