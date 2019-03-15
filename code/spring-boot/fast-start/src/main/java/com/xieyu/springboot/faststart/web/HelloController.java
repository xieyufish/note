package com.xieyu.springboot.faststart.web;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * <b>类作用描述：</b>
 * <pre>
 * 
 * </pre>
 * <b>创建者：</b>  	xieyu <br>
 * <b>所属项目：</b>	fast-start <br>
 * <b>创建日期：</b>	2019年03月15日 11:22:22 <br>
 * <b>修订记录：</b>	<br>
 * <b>当前版本：</b>	1.0.0 <br>
 * <b>参考：</b>		
 */
@RestController
public class HelloController {
	
	@RequestMapping("/hello")
	public String index() {
		return "Hello World";
	}
}
