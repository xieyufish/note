package com.xieyu.springboot.rabbitmq;

import org.springframework.amqp.rabbit.annotation.RabbitHandler;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * <b>类作用描述：</b>
 * <pre>
 * 
 * </pre>
 * <b>创建者：</b>  	xieyu <br>
 * <b>所属项目：</b>	rabbitmq <br>
 * <b>创建日期：</b>	2019年03月22日 15:22:20 <br>
 * <b>修订记录：</b>	<br>
 * <b>当前版本：</b>	1.0.0 <br>
 * <b>参考：</b>		
 */
@Component
@RabbitListener(queues = "hello")
public class Receiver {
	
	@RabbitHandler
	public void process(String hello) {
		System.out.println("Receiver: " + hello);
	}
}
