package com.xieyu.springboot.springapplication;

import org.springframework.boot.Banner;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;

/**
 * <b>类作用描述：</b>
 * <pre>
 * 流式API方式构建SpringApplication，如果我们构建的Spring容器是有层级关系或者是你比较偏爱用流式的方式构建，那么你可以使用SpringApplicationBuilder
 * </pre>
 * <b>创建者：</b>  	xieyu <br>
 * <b>所属项目：</b>	springapplication <br>
 * <b>创建日期：</b>	2019年03月15日 16:37:15 <br>
 * <b>修订记录：</b>	<br>
 * <b>当前版本：</b>	1.0.0 <br>
 * <b>参考：</b>		
 */
@SpringBootApplication
public class FluentBuilderAPI {
	public static void main(String[] args) {
//		new SpringApplicationBuilder().sources(FluentBuilderAPI.class).child(ChildApplication.class).bannerMode(Banner.Mode.OFF).run(args);
		new SpringApplicationBuilder().sources(FluentBuilderAPI.class).bannerMode(Banner.Mode.OFF).run(args);
	}
}
