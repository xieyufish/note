package com.xieyu.springboot.springapplication;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * <b>类作用描述：</b>
 * <pre>
 * 自定义打印的Banner:
 * 1. 默认通过在类路径下添加banner.txt,banner.jpg,banner.png,banner.gif等文件可以覆盖Springboot默认输出的Banner
 * 2. 在banner.txt文件中，可以通过${}使用一些springboot提供的变量
 * 3. 图片banner在控制台打印的时候会输出ASCII对应的字符
 * 4. 如果banner.txt的编码不是UTF8,那么可以在配置文件中添加spring.banner.charset指定编码格式
 * 5. 可以通过在配置文件添加spring.banner.location指定banner文件的位置
 * 6. 可以通过在配置文件添加spring.banner.image.location指定banner图片文件的位置
 * 7. 可以通过在配置文件指定spring.main.banner-mode值来配置是否打印banner
 * </pre>
 * <b>创建者：</b>  	xieyu <br>
 * <b>所属项目：</b>	springapplication <br>
 * <b>创建日期：</b>	2019年03月15日 15:55:22 <br>
 * <b>修订记录：</b>	<br>
 * <b>当前版本：</b>	1.0.0 <br>
 * <b>参考：</b>		
 */
@SpringBootApplication
public class CustomizeBanner {
	
	public static void main(String[] args) {
		SpringApplication.run(CustomizeBanner.class, args);
	}
}
