package com.xieyu.study.jndi;

import java.util.Hashtable;

import javax.naming.Context;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;

/**
 * <b>类作用描述：</b>
 * <pre>
 * 
 * </pre>
 * <b>创建者：</b>  	xieyu <br>
 * <b>所属项目：</b>	jndi <br>
 * <b>创建日期：</b>	2018年11月08日 20:04:03 <br>
 * <b>修订记录：</b>	<br>
 * <b>当前版本：</b>	1.0.0 <br>
 * <b>参考：</b>		
 */
public class DNSQuery {
	
	public static void main(String[] args) throws NamingException {
		
		String domain = args[0];
		String dnsServer = args.length < 2 ? "" : ("//" + args[1]);
		
		Hashtable<String, String> env = new Hashtable<String, String>();
		env.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.dns.DnsContextFactory");
		env.put(Context.PROVIDER_URL, "dns:" + dnsServer);
		
		DirContext ctx = new InitialDirContext(env);
		Attributes attrsAll = ctx.getAttributes(domain);
		Attributes attrsMX = ctx.getAttributes(domain, new String[]{"MX"});
		
		System.out.println("打印出域名：" + domain + "的Attributes对象中的信息：");
		System.out.println(attrsAll);
		System.out.println("--------------------");
		System.out.println("打印只检索域" + domain + "的MX记录的Attributes对象");
		System.out.println(attrsMX);
		
		System.out.println("----------------------------");
		System.out.println("逐一打印出Attributes对象中的各个属性：");
		NamingEnumeration<? extends Attribute> attributes = attrsAll.getAll();
		while (attributes.hasMore()) {
			System.out.println(attributes.next());
		}
		
		System.out.println("-----------------------");
		System.out.println("直接检索Attributes对象中的MX属性：");
		Attribute attrMX = attrsAll.get("MX");
		System.out.println(attrMX);
		
		System.out.println("----------------------");
		System.out.println("获取MX属性中的第一个值：");
		String recordMx = (String)attrMX.get();
		System.out.println(recordMx);
		System.out.println("从MX属性值中提取的邮件服务器地址：");
		String smtpServer = recordMx.substring(recordMx.indexOf(" ") + 1);
		System.out.println(smtpServer);
	}
}
