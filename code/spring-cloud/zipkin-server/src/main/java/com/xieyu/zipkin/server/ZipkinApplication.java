package com.xieyu.zipkin.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import zipkin.server.EnableZipkinServer;

/**
 * <b>类作用描述：</b>
 * <pre>
 * 
 * </pre>
 * <b>创建者：</b>  	xieyu <br>
 * <b>所属项目：</b>	zipkin-server <br>
 * <b>创建日期：</b>	2019年03月26日 11:06:44 <br>
 * <b>修订记录：</b>	<br>
 * <b>当前版本：</b>	1.0.0 <br>
 * <b>参考：</b>		
 */
@EnableZipkinServer
@SpringBootApplication
public class ZipkinApplication {
	public static void main(String[] args) {
		SpringApplication.run(ZipkinApplication.class, args);
	}
}
