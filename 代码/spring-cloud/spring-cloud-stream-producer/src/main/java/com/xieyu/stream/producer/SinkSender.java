package com.xieyu.stream.producer;

import org.springframework.cloud.stream.annotation.EnableBinding;
import org.springframework.cloud.stream.messaging.Source;
import org.springframework.context.annotation.Bean;
import org.springframework.integration.annotation.InboundChannelAdapter;
import org.springframework.integration.annotation.Poller;
import org.springframework.integration.core.MessageSource;
import org.springframework.messaging.support.GenericMessage;

/**
 * <b>类作用描述：</b>
 * <pre>
 * 
 * </pre>
 * <b>创建者：</b>  	xieyu <br>
 * <b>所属项目：</b>	spring-cloud-stream-consumer-group <br>
 * <b>创建日期：</b>	2019年03月25日 19:02:42 <br>
 * <b>修订记录：</b>	<br>
 * <b>当前版本：</b>	1.0.0 <br>
 * <b>参考：</b>		
 */
@EnableBinding(value = {Source.class})
public class SinkSender {
	
//	private static Logger logger = LoggerFactory.getLogger(SinkSender.class);
	
	@Bean
	@InboundChannelAdapter(value = Source.OUTPUT, poller = @Poller(fixedDelay = "2000"))
	public MessageSource<String> timerMessageSource() {
		return () -> new GenericMessage<>("{\"name\":\"didi\", \"age\":30}");
	}
}
