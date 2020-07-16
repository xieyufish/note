package com.xieyu.springboot.rabbitmq;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

/**
 * <b>类作用描述：</b>
 * <pre>
 * 
 * </pre>
 * <b>创建者：</b>  	xieyu <br>
 * <b>所属项目：</b>	rabbitmq <br>
 * <b>创建日期：</b>	2019年03月22日 15:29:49 <br>
 * <b>修订记录：</b>	<br>
 * <b>当前版本：</b>	1.0.0 <br>
 * <b>参考：</b>		
 */
@RunWith(SpringRunner.class)
@SpringBootTest(classes = HelloApplication.class)
public class HelloApplicationTests {
	
	@Autowired
	private Sender sender;
	
	@Test
	public void hello() {
		sender.send();
	}
}
