package com.xieyu.zookeeper.client;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * <b>类作用描述：</b>
 * <pre>
 * 
 * </pre>
 * <b>创建者：</b>  	xieyu <br>
 * <b>所属项目：</b>	spring-cloud-zookeeper-client <br>
 * <b>创建日期：</b>	2019年03月18日 19:02:36 <br>
 * <b>修订记录：</b>	<br>
 * <b>当前版本：</b>	1.0.0 <br>
 * <b>参考：</b>		
 */
@EnableDiscoveryClient
@SpringBootApplication
public class ZookeeperClientApplication {
	public static void main(String[] args) {
		SpringApplication.run(ZookeeperClientApplication.class, args);
	}
}
