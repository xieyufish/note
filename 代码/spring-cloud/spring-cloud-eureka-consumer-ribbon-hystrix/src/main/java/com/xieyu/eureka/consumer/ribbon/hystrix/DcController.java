package com.xieyu.eureka.consumer.ribbon.hystrix;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * <b>类作用描述：</b>
 * <pre>
 * 
 * </pre>
 * <b>创建者：</b>  	xieyu <br>
 * <b>所属项目：</b>	spring-cloud-eureka-consumer-ribbon-hystrix <br>
 * <b>创建日期：</b>	2019年03月19日 17:42:11 <br>
 * <b>修订记录：</b>	<br>
 * <b>当前版本：</b>	1.0.0 <br>
 * <b>参考：</b>		
 */
@RestController
public class DcController {
	
	@Autowired
	private ConsumerService consumerService;
	
	@GetMapping("/consumer")
	public String dc() {
		return consumerService.consumer();
	}
}
