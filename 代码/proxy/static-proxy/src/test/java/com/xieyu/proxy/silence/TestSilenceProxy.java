package com.xieyu.proxy.silence;

import org.junit.Test;

/**
 * <b>类作用描述：</b>
 * <pre>
 * 
 * </pre>
 * <b>创建者：</b>  	xieyu <br>
 * <b>所属项目：</b>	static-proxy <br>
 * <b>创建日期：</b>	2019年03月27日 11:59:25 <br>
 * <b>修订记录：</b>	<br>
 * <b>当前版本：</b>	1.0.0 <br>
 * <b>参考：</b>		
 */
public class TestSilenceProxy {
	
	@Test
	public void testSilenceProxy() {
		TargetPerson target = new TargetPerson("I am a famous actor!");
		ProxyPerson proxy = new ProxyPerson(target, "Hello I am an agent", "That's all!");
		proxy.speak();
	}
}
