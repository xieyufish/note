package com.xieyu.proxy.silence;

/**
 * <b>类作用描述：</b>
 * <pre>
 * 
 * </pre>
 * <b>创建者：</b>  	xieyu <br>
 * <b>所属项目：</b>	static-proxy <br>
 * <b>创建日期：</b>	2019年03月27日 11:54:52 <br>
 * <b>修订记录：</b>	<br>
 * <b>当前版本：</b>	1.0.0 <br>
 * <b>参考：</b>		
 */
public class TargetPerson implements Person {
	
	private String content;
	
	public TargetPerson(String content) {
		this.content = content;
	}

	@Override
	public void speak() {
		System.out.println(this.content);
	}
}
