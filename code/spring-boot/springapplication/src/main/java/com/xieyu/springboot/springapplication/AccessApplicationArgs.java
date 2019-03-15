package com.xieyu.springboot.springapplication;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;

/**
 * <b>类作用描述：</b>
 * <pre>
 * 访问通过SpringApplication.run(..,args)方式传入的参数，你可以通过注入org.springframework.boot.ApplicationArguments来实现
 * 输入的参数形式：--name=value --name1=value1
 * </pre>
 * <b>创建者：</b>  	xieyu <br>
 * <b>所属项目：</b>	springapplication <br>
 * <b>创建日期：</b>	2019年03月15日 16:57:34 <br>
 * <b>修订记录：</b>	<br>
 * <b>当前版本：</b>	1.0.0 <br>
 * <b>参考：</b>		
 */
@SpringBootApplication
public class AccessApplicationArgs {
	
	private ApplicationArguments args;
	
	public static void main(String[] args) {
		ApplicationContext context = SpringApplication.run(AccessApplicationArgs.class, args);
		AccessApplicationArgs accessApplicationArgs = context.getBean(AccessApplicationArgs.class);
		accessApplicationArgs.print();
	}
	
	@Autowired
	public AccessApplicationArgs(ApplicationArguments args) {
		this.args = args;
	}
	
	public void print() {
		System.out.println(this.args.getOptionValues("a"));
		System.out.println(this.args.getOptionValues("b"));
	}
}
