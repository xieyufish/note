package com.xieyu.rabbitmy.workqueues;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.DeliverCallback;

/**
 * <b>类作用描述：</b>
 * <pre>
 * </pre>
 * <b>创建者：</b>  	xieyu <br>
 * <b>所属项目：</b>	work-queues <br>
 * <b>创建日期：</b>	2019年03月22日 21:27:55 <br>
 * <b>修订记录：</b>	<br>
 * <b>当前版本：</b>	1.0.0 <br>
 * <b>参考：</b>		
 */
public class Worker {
	
	private static final String QUEUE_NAME = "hello";
	
	public static void main(String[] args) {
		ConnectionFactory factory = new ConnectionFactory();
		try {
			Connection connection = factory.newConnection();
			Channel channel = connection.createChannel();
			
			channel.queueDeclare(QUEUE_NAME, false, false, false, null);
			
			channel.basicQos(1);
			DeliverCallback deliverCallback = (consumerTag, delivery) -> {
				String message = new String(delivery.getBody(), "UTF-8");
				
				System.out.println("[x] Received: '" + message + "'");
				
				try {
					doWork(message);
				} catch (InterruptedException e) {
					e.printStackTrace();
				} finally {
					System.out.println(" [x] Done");
					channel.basicAck(delivery.getEnvelope().getDeliveryTag(), false);
				}
			};
			
//			boolean autoAck = true;
			boolean autoAck = false;
			channel.basicConsume(QUEUE_NAME, autoAck, deliverCallback, consumerTag -> {});
		} catch (Exception e) {
			
		}
	}
	
	public static void doWork(String message) throws InterruptedException {
		for (char ch : message.toCharArray()) {
			if (ch == '.') Thread.sleep(1000);
		}
	}
}
