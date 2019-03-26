package com.xieyu.stream.consumer.group;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.stream.annotation.EnableBinding;
import org.springframework.cloud.stream.annotation.StreamListener;
import org.springframework.cloud.stream.messaging.Sink;

/**
 * <b>类作用描述：</b>
 * <pre>
 * 
 * </pre>
 * <b>创建者：</b>  	xieyu <br>
 * <b>所属项目：</b>	spring-cloud-stream-consumer-group <br>
 * <b>创建日期：</b>	2019年03月25日 18:58:48 <br>
 * <b>修订记录：</b>	<br>
 * <b>当前版本：</b>	1.0.0 <br>
 * <b>参考：</b>		
 */
@EnableBinding(value = {Sink.class})
public class SinkReceiver {
	
	private static Logger logger = LoggerFactory.getLogger(SinkReceiver.class);
	
	@StreamListener(Sink.INPUT)
	public void receive(User user) {
		logger.info("Received: " + user);
	}
}
