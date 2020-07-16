package com.xieyu.trace.second;

import javax.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * <b>类作用描述：</b>
 * <pre>
 * 
 * </pre>
 * <b>创建者：</b>  	xieyu <br>
 * <b>所属项目：</b>	spring-cloud-trace-2 <br>
 * <b>创建日期：</b>	2019年03月25日 19:41:02 <br>
 * <b>修订记录：</b>	<br>
 * <b>当前版本：</b>	1.0.0 <br>
 * <b>参考：</b>		
 */
@RestController
@EnableDiscoveryClient
@SpringBootApplication
public class TraceApplication {
	
	private final static Logger logger = LoggerFactory.getLogger(TraceApplication.class);
	
	@GetMapping("/trace-2")
	public String trace(HttpServletRequest request) {
		logger.info("===<call trace-2, TraceId={}, SpanId={}>===", 
			request.getHeader("X-B3-TraceId"), request.getHeader("X-B3-SpanId"));
		return "Trace";
	}
	
	public static void main(String[] args) {
		SpringApplication.run(TraceApplication.class, args);
	}
}
