package com.xieyu.rabbitmq.rpc;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeoutException;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;

public class RPCClient implements AutoCloseable {
	private Connection connection;
	private Channel channel;
	private String requestQueueName = "rpc_queue";
	
	public RPCClient() throws IOException, TimeoutException {
		ConnectionFactory connectionFacotry = new ConnectionFactory();
		connection = connectionFacotry.newConnection();
		channel = connection.createChannel();
	}
	
	public static void main(String[] args) throws TimeoutException, Exception {
		try (RPCClient fibonacciRpc = new RPCClient()) {
			for (int i = 0; i < 32; i++) {
				String i_str = Integer.toString(i);
				System.out.println(" [x] Requesting fib(" + i_str + ")");
				String response = fibonacciRpc.call(i_str);
				System.out.println(" [.] Got '" + response + "'");
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	public String call(String message) throws UnsupportedEncodingException, IOException, InterruptedException {
		final String corrId = UUID.randomUUID().toString();
		
		String replyQueueName = channel.queueDeclare().getQueue();
		AMQP.BasicProperties props = new AMQP.BasicProperties.Builder().correlationId(corrId).replyTo(replyQueueName).build();
		
		channel.basicPublish("", requestQueueName, props, message.getBytes("UTF-8"));
		final BlockingQueue<String> response = new ArrayBlockingQueue<>(1);
		
		String ctag = channel.basicConsume(replyQueueName, true, (consumerTag, delivery) -> {
			if (delivery.getProperties().getCorrelationId().equals(corrId)) {
				response.offer(new String(delivery.getBody(), "UTF-8"));
			}
		}, consumerTag -> {});
		
		String result = response.take();
		channel.basicCancel(ctag);
		return result;
	}

	@Override
	public void close() throws Exception {
		connection.close();
	}
}
