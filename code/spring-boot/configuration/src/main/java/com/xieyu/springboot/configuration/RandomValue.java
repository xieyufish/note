package com.xieyu.springboot.configuration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;

/**
 * <b>类作用描述：</b>
 * <pre>
 * 随机值配置
 * </pre>
 * <b>创建者：</b>  	xieyu <br>
 * <b>所属项目：</b>	configuration <br>
 * <b>创建日期：</b>	2019年03月15日 19:15:40 <br>
 * <b>修订记录：</b>	<br>
 * <b>当前版本：</b>	1.0.0 <br>
 * <b>参考：</b>		
 */
@SpringBootApplication
public class RandomValue {
	
	@Value("${my.secret}")
	private String secret;
	
	@Value("${my.number}")
	private int number;
	
	@Value("${my.bignumber}")
	private long bignumber;
	
	@Value("${my.uuid}")
	private String uuid;
	
	@Value("${my.number.less.than.ten}")
	private int lessTen;
	
	@Value("${my.number.in.range}")
	private int range;
	
	public static void main(String[] args) {
		ApplicationContext context = SpringApplication.run(RandomValue.class, args);
		RandomValue randValue = context.getBean(RandomValue.class);
		System.out.println(randValue.secret);
		System.out.println(randValue.number);
		System.out.println(randValue.bignumber);
		System.out.println(randValue.uuid);
		System.out.println(randValue.lessTen);
		System.out.println(randValue.range);
	}
}
