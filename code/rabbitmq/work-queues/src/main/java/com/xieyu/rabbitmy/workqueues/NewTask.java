package com.xieyu.rabbitmy.workqueues;

import java.io.IOException;
import java.util.concurrent.TimeoutException;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.MessageProperties;

/**
 * <b>类作用描述：</b>
 * <pre>
 * </pre>
 * <b>创建者：</b>  	xieyu <br>
 * <b>所属项目：</b>	work-queues <br>
 * <b>创建日期：</b>	2019年03月22日 21:24:51 <br>
 * <b>修订记录：</b>	<br>
 * <b>当前版本：</b>	1.0.0 <br>
 * <b>参考：</b>		
 */
public class NewTask {
	
	private static final String QUEUE_NAME = "hello";
	
	public static void main(String[] args) {
		ConnectionFactory factory = new ConnectionFactory();
		try (Connection connection = factory.newConnection();
			 Channel channel = connection.createChannel();) {
			
//			channel.queueDeclare(QUEUE_NAME, false, false, false, null);
			channel.queueDeclare(QUEUE_NAME, true, false, false, null);
			String message = String.join(" ", args);
			System.out.println(" [x] Send: '" + message + "'");
//			channel.basicPublish("", QUEUE_NAME, null, message.getBytes());
			channel.basicPublish("", QUEUE_NAME, MessageProperties.PERSISTENT_TEXT_PLAIN, message.getBytes());	//让消息持久化存储到磁盘
		} catch (IOException e) {
			e.printStackTrace();
		} catch (TimeoutException e) {
			e.printStackTrace();
		}
	}
}
