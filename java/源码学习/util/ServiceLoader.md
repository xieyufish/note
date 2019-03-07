# java源码解析-ServiceLoader类之SPI机制

##一、什么是SPI？

SPI，Service Provider Interface(服务提供接口)的缩写；什么意思呢？比如你有个接口，现在这个接口有 3 个实现类，那么在系统运行的时候到底选择这个接口哪个实现类呢？这就需要 SPI 了，需要**根据指定的配置**或者是**默认的配置**，去**找到对应的实现类**加载进来，然后用这个实现类的实例对象。

举个例子，假设我有一个接口A，A1/A2/A3是提供的接口A的不同实现。那么我可以通过配置`A=A3`，在系统实际运行的时候加载这个配置，用A3实例化一个对象来提供服务。如果我想将实现改为A2，那么只需将配置修改为`A=A2`，再运行时，就会用A2实例化一个对象来提供服务。

## 二、SPI的使用场景

SPI机制一般的使用场景是什么呢？**插件扩展的场景**，比如说你开发了一个给别人使用的开源框架，如果你想让别人自定义实现某个功能，插到你的开源框架里面，从而扩展这个功能，这个时候 spi 思想就用上了。

SPI的经典实现其实大家平常都在接触，那就是JDBC。我们知道在Java中只定义了一套JDBC的接口，具体的实现都是由具体的数据库厂商提供，比如我们使用Mysql数据库，就用mysql-connector-java.jar；使用oracle数据库，就用oracle-connector-java.jar。在运行过程中，实例化JDBC接口实现，就会自动找到我们所使用jar的实现。那么这个查找过程和实例化过程是怎么样的呢？Java提供了**java.util.ServiceLoader**这个类来发现实现和实例化类，我们来看看ServiceLoader的实现。

##三、ServiceLoader类

### 3.1 ServiceLoader的使用

#### 3.1.1 提供接口

现在我提供了一个接口Spi，代码如下：

``````java
package com.shell.spi;

/**
 * <b>类作用描述：</b><br>
 * <p>
 * 
 * </p>
 * <br><b>创建者：</b>  	xieyu
 * <br><b>所属项目：</b>	spi
 * <br><b>创建日期：</b>	2017年04月25日 19:03:50
 * <br><b>修订记录：</b>	
 * <br><b>当前版本：</b>	1.0.0
 * <br><b>参考：<b>		
 */
public interface Spi {
	void execute();
}
``````

#### 3.1.2 提供实现

现在我提供两个实现上述接口的实现类SpiProviderFirst和SpiProviderSecond，具体实现如下：

``````java
package com.shell.spi_provider1;

import com.shell.spi.Spi;

/**
 * <b>类作用描述：</b><br>
 * <p>
 * 
 * </p>
 * <br><b>创建者：</b>  	xieyu
 * <br><b>所属项目：</b>	spi_provider1
 * <br><b>创建日期：</b>	2017年04月25日 19:05:31
 * <br><b>修订记录：</b>	
 * <br><b>当前版本：</b>	1.0.0
 * <br><b>参考：<b>		
 */
public class SpiProviderFirst implements Spi {

	@Override
	public void execute() {
		System.out.println("SpiProviderFirst");
	}

}
``````

``````java
package com.shell.spi_provider2;

import com.shell.spi.Spi;

/**
 * <b>类作用描述：</b><br>
 * <p>
 * 
 * </p>
 * <br><b>创建者：</b>  	xieyu
 * <br><b>所属项目：</b>	spi_provider2
 * <br><b>创建日期：</b>	2017年04月25日 19:10:02
 * <br><b>修订记录：</b>	
 * <br><b>当前版本：</b>	1.0.0
 * <br><b>参考：<b>		
 */
public class SpiProviderSecond implements Spi {

	@Override
	public void execute() {
		System.out.println("SpiProviderSecond");
	}

}
``````

#### 3.1.3 调用实现

在项目工程下，添加META-INF/services目录，并在此目录下创建以接口权限定名命名的文件(此处文件名为com.shell.spi.Spi)，文件内容即为你想要配置此接口的实现类，我这里文件内容为：

``````text
com.shell.spi_provider2.SpiProviderSecond
``````

测试代码如下:

``````java
package com.shell.spi_test;

import java.util.ServiceLoader;

import com.shell.spi.Spi;

/**
 * <b>类作用描述：</b><br>
 * <p>
 * 
 * </p>
 * <br><b>创建者：</b>  	xieyu
 * <br><b>所属项目：</b>	spi_test
 * <br><b>创建日期：</b>	2017年04月25日 19:12:49
 * <br><b>修订记录：</b>	
 * <br><b>当前版本：</b>	1.0.0
 * <br><b>参考：<b>		
 */
public class SpiTest {
	public static void main(String[] args) {
		ServiceLoader<Spi> serviceLoader=ServiceLoader.load(Spi.class); 
		int i = 0;
        for(Spi spi:serviceLoader){  
        	i ++;
            spi.execute();  
        }  
        System.out.println(i);
	}
}
``````

源码地址

### 3.2 源码分析

