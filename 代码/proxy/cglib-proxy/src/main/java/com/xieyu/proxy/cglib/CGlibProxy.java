package com.xieyu.proxy.cglib;

import java.lang.reflect.Method;

import net.sf.cglib.proxy.Enhancer;
import net.sf.cglib.proxy.MethodInterceptor;
import net.sf.cglib.proxy.MethodProxy;

/**
 * <b>类作用描述：</b>
 * <pre>
 * 
 * </pre>
 * <b>创建者：</b>  	xieyu <br>
 * <b>所属项目：</b>	cglib-proxy <br>
 * <b>创建日期：</b>	2019年03月27日 13:44:23 <br>
 * <b>修订记录：</b>	<br>
 * <b>当前版本：</b>	1.0.0 <br>
 * <b>参考：</b>		
 */
public class CGlibProxy implements MethodInterceptor {
	
//	private Object proxy;
	
	public Object getInstance(Object proxy) {
//		this.proxy = proxy;
		Enhancer enhancer = new Enhancer();
		enhancer.setSuperclass(proxy.getClass());
		enhancer.setCallback(this);
		return enhancer.create();
	}

	@Override
	public Object intercept(Object obj, Method method, Object[] args, MethodProxy proxy)
		throws Throwable {
		System.out.println(">>>>before invoking");
		System.out.println(obj.getClass());
		System.out.println(proxy.getClass());
		System.out.println(proxy.getSignature());
		Object ret = proxy.invokeSuper(obj, args);
		System.out.println(">>>>after invoking");
		return ret;
	}
	
	public static void main(String[] args) {
		CGlibProxy proxy = new CGlibProxy();
		Apple apple = (Apple) proxy.getInstance(new Apple());
		apple.show();
	}

}
