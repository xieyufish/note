package com.xieyu.trace.first;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

/**
 * <b>类作用描述：</b>
 * <pre>
 * 
 * </pre>
 * <b>创建者：</b>  	xieyu <br>
 * <b>所属项目：</b>	spring-cloud-trace-1 <br>
 * <b>创建日期：</b>	2019年03月25日 19:34:07 <br>
 * <b>修订记录：</b>	<br>
 * <b>当前版本：</b>	1.0.0 <br>
 * <b>参考：</b>		
 */
@RestController
public class TraceController {
	private static final Logger logger = LoggerFactory.getLogger(TraceController.class);
	
	@Autowired
	private RestTemplate restTemplate;
	
	@GetMapping("/trace-1")
	public String trace() {
		logger.info("===call trace-1===");
		return restTemplate.getForEntity("http://trace-2/trace-2", String.class).getBody();
	}
}
