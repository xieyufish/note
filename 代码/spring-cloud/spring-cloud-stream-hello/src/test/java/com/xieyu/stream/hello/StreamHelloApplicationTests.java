package com.xieyu.stream.hello;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.stream.annotation.EnableBinding;
import org.springframework.cloud.stream.annotation.Output;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.test.context.junit4.SpringRunner;

/**
 * <b>类作用描述：</b>
 * <pre>
 * 
 * </pre>
 * <b>创建者：</b>  	xieyu <br>
 * <b>所属项目：</b>	spring-cloud-stream-hello <br>
 * <b>创建日期：</b>	2019年03月21日 14:10:19 <br>
 * <b>修订记录：</b>	<br>
 * <b>当前版本：</b>	1.0.0 <br>
 * <b>参考：</b>		
 */
@RunWith(SpringRunner.class)
@EnableBinding({StreamHelloApplicationTests.HelloMessageSender.class})
public class StreamHelloApplicationTests {
	
	@Autowired
	private HelloMessageSender helloMessageSender;
	
	@Test
	public void helloMessageSenderTester() {
		helloMessageSender.output().send(MessageBuilder.withPayload("produce a message: hello").build());
	}
	
	public interface HelloMessageSender {
		String OUTPUT = "input";
		
		@Output(HelloMessageSender.OUTPUT)
		MessageChannel output();
	}
}
