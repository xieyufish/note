package com.xieyu.rabbitmq.topic;

import java.io.IOException;
import java.util.concurrent.TimeoutException;

import com.rabbitmq.client.BuiltinExchangeType;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.DeliverCallback;

public class ReceiveLogsTopic {
	
	private static final String EXCHANGE_NAME = "topic_logs";
	
	public static void main(String[] args) throws IOException, TimeoutException {
		ConnectionFactory factory = new ConnectionFactory();
		Connection connection = factory.newConnection();
		Channel channel = connection.createChannel();
		
		channel.exchangeDeclare(EXCHANGE_NAME, BuiltinExchangeType.TOPIC);
		String queueName = channel.queueDeclare().getQueue();
		
		if (args.length < 1) {
	        System.err.println("Usage: ReceiveLogsTopic [binding_key]...");
	        System.exit(1);
	    }

	    for (String bindingKey : args) {
	        channel.queueBind(queueName, EXCHANGE_NAME, bindingKey);
	    }
	    
	    System.out.println(" [*] Waiting for messages. To exit press CTRL+C");
	    
	    DeliverCallback deliverCallback = (consumerTag, delivery) -> {
	    	String message = new String(delivery.getBody(), "UTF-8");
	    	System.out.println(" [x] Received '" +
	                delivery.getEnvelope().getRoutingKey() + "':'" + message + "'");
	    };
	    
	    channel.basicConsume(queueName, true, deliverCallback, consumerTag -> {});
	}
}
