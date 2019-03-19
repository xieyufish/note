package com.xieyu.config.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * <b>类作用描述：</b>
 * <pre>
 * 
 * </pre>
 * <b>创建者：</b>  	xieyu <br>
 * <b>所属项目：</b>	spring-cloud-config-client <br>
 * <b>创建日期：</b>	2019年03月19日 15:37:02 <br>
 * <b>修订记录：</b>	<br>
 * <b>当前版本：</b>	1.0.0 <br>
 * <b>参考：</b>		
 */
@RefreshScope
@RestController
public class TestController {
	@Value("${info.profile}")
	private String info;
	
	@RequestMapping("/from")
	public String info() {
		return this.info;
	}
}
