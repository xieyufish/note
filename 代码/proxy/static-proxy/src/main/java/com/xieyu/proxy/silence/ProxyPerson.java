package com.xieyu.proxy.silence;

/**
 * <b>类作用描述：</b>
 * <pre>
 * 
 * </pre>
 * <b>创建者：</b>  	xieyu <br>
 * <b>所属项目：</b>	static-proxy <br>
 * <b>创建日期：</b>	2019年03月27日 11:56:09 <br>
 * <b>修订记录：</b>	<br>
 * <b>当前版本：</b>	1.0.0 <br>
 * <b>参考：</b>		
 */
public class ProxyPerson implements Person {
	private TargetPerson target;
	private String before;
	private String after;
	
	public ProxyPerson(TargetPerson target, String before, String after) {
		this.target = target;
		this.before = before;
		this.after = after;
	}

	@Override
	public void speak() {
		System.out.println("Before target speak, Proxy say: " + before);
		target.speak();
		System.out.println("After target speak, Proxy say: " + after);
	}

}
