package com.xieyu.springboot.configuration;

/**
 * <b>类作用描述：</b>
 * <pre>
 * </pre>
 * <b>创建者：</b>  	xieyu <br>
 * <b>所属项目：</b>	configuration <br>
 * <b>创建日期：</b>	2019年03月16日 17:54:46 <br>
 * <b>修订记录：</b>	<br>
 * <b>当前版本：</b>	1.0.0 <br>
 * <b>参考：</b>		
 */
public class ApplicationPropertiesFile {
	/**
	 * SpringApplication从如下地方加载application.properties配置文件：
	 * 1. 当前目录下的config子目录；
	 * 2. 当前目录；
	 * 3. 类路径下的config子目录；
	 * 4. 类路径的根路径
	 * 上述4个的优先级逐渐下降。如果你不想用application.properties作为配置文件的名称，你可以通过选项参数--spring.config.name=myproject指定一个配置文件的名称；
	 * 你也可以通过--spring.config.location选项参数指定配置文件的位置
	 * 例如：
	 * java -jar myproject.jar --spring.config.name=myproject
	 * java -jar myproject.jar --spring.config.location=classpath:/default.properties,classpath:/override.properties
	 * 因为spring.config.name和spring.config.location在SpringApplication启动阶段的最开始就要使用到，所以只能通过系统环境变量、系统属性或者是命令行参数来尽早的指定值
	 * 
	 * 如果spring.config.location值包含目录，则必须以/结束，在加载时会自动将spring.config.name的值拼接在目录路径的后面。
	 * spring.config.location指定多个值时，是按反序加载的，例如：默认的配置路径值为classpath:/,classpath:/config/,file:./,file:./config/时，则搜索顺序为：
	 * 1. file:./config/
	 * 2. file:./
	 * 3. classpath:/config/
	 * 4. classpath:/
	 * 
	 * 当指定了spring.config.location值时，默认的配置路径被替代，如：配置值为classpath:/custom-config/,file:./custom-config/时，搜索顺序为：
	 * 1. file:./custom-config/
	 * 2. classpath:/custom-config/
	 * 
	 * 如果不想自己指定的配置文件路径替换掉默认的路径，那么可以通过--spring.config.additional-location来指定自定义的配置文件路径，例如如果这个属性的值指定为：
	 * classpath:/custom-config/,file:./custom-config/，那么搜索顺序为：
	 * 1. file:./custom-config/
	 * 2. classpath:/custom-config/
	 * 3. file:./config/
	 * 4. file:./
	 * 5. classpath:/config/
	 * 6. classpath:/
	 */
}
