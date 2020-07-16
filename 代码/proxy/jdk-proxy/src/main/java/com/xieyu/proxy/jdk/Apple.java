package com.xieyu.proxy.jdk;

/**
 * <b>类作用描述：</b>
 * <pre>
 * 
 * </pre>
 * <b>创建者：</b>  	xieyu <br>
 * <b>所属项目：</b>	jdk-proxy <br>
 * <b>创建日期：</b>	2019年03月27日 12:17:04 <br>
 * <b>修订记录：</b>	<br>
 * <b>当前版本：</b>	1.0.0 <br>
 * <b>参考：</b>		
 */
public class Apple implements Fruit {

	@Override
	public void show() {
		System.out.println("<<<<show method is invoked");
	}

}
