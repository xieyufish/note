package com.xieyu.springboot.rabbitmq;

import org.springframework.amqp.core.Queue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * <b>类作用描述：</b>
 * <pre>
 * 
 * </pre>
 * <b>创建者：</b>  	xieyu <br>
 * <b>所属项目：</b>	rabbitmq <br>
 * <b>创建日期：</b>	2019年03月22日 15:24:50 <br>
 * <b>修订记录：</b>	<br>
 * <b>当前版本：</b>	1.0.0 <br>
 * <b>参考：</b>		
 */
@Configuration
public class RabbitConfig {
	
	@Bean
	public Queue helloQueue() {
		return new Queue("hello");
	}
}
