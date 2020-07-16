package com.xieyu.proxy.jdk;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/**
 * <b>类作用描述：</b>
 * <pre>
 * 
 * </pre>
 * <b>创建者：</b>  	xieyu <br>
 * <b>所属项目：</b>	jdk-proxy <br>
 * <b>创建日期：</b>	2019年03月27日 12:18:19 <br>
 * <b>修订记录：</b>	<br>
 * <b>当前版本：</b>	1.0.0 <br>
 * <b>参考：</b>		
 */
public class JDKProxy {
	static class MyHandler implements InvocationHandler {
		
		private Object proxy;
		
		public MyHandler(Object proxy) {
			this.proxy = proxy;
		}

		@Override
		public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
			System.out.println(">>>>before invoking");
			Object ret = method.invoke(this.proxy, args);
			System.out.println(">>>>after invoking");
			return ret;
		}
		
	}
	
	public static Object proxy(Class<?> interfaceClazz, Object proxy) {
		return Proxy.newProxyInstance(interfaceClazz.getClassLoader(), new Class[] {interfaceClazz}, new MyHandler(proxy));
	}
}
