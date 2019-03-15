# java源码解析-ServiceLoader类之SPI机制

##一、什么是SPI？

SPI，Service Provider Interface(服务提供接口)的缩写；什么意思呢？比如你有个接口，现在这个接口有 3 个实现类，那么在系统运行的时候到底选择这个接口哪个实现类呢？这就需要 SPI 了，需要**根据指定的配置**或者是**默认的配置**，去**找到对应的实现类**加载进来，然后用这个实现类的实例对象。

举个例子，假设我有一个接口A，A1/A2/A3是提供的接口A的不同实现。那么我可以通过配置`A=A3`，在系统实际运行的时候加载这个配置，用A3实例化一个对象来提供服务。如果我想将实现改为A2，那么只需将配置修改为`A=A2`，再运行时，就会用A2实例化一个对象来提供服务。

## 二、SPI的使用场景

SPI机制一般的使用场景是什么呢？**插件扩展的场景**，比如说你开发了一个给别人使用的开源框架，如果你想让别人自定义实现某个功能，插到你的开源框架里面，从而扩展这个功能，这个时候 spi 思想就用上了。

SPI的经典实现其实大家平常都在接触，那就是JDBC。我们知道在Java中只定义了一套JDBC的接口，具体的实现都是由具体的数据库厂商提供，比如我们使用Mysql数据库，就用mysql-connector-java.jar；使用oracle数据库，就用oracle-connector-java.jar。在运行过程中，实例化JDBC接口实现，就会自动找到我们所使用jar的实现。那么这个查找过程和实例化过程是怎么样的呢？Java提供了**java.util.ServiceLoader**这个类来发现实现和实例化类，我们来看看ServiceLoader的实现。

##三、ServiceLoader类

### 3.1 ServiceLoader的使用示例

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
        // ServiceLoader的load方法会读取META-INF/services目录下的文件
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

### 3.2 源码分析

从上面示例我们可以知道，ServiceLoader类通过load方法来返回一个ServiceLoader的实例。同时我们从ServiceLoader类的实现可以知道，load方法也是它的主要入口。下面我们分析一下load方法的具体实现，代码如下：

``````java
public static <S> ServiceLoader<S> load(Class<S> service) {
    ClassLoader cl = Thread.currentThread().getContextClassLoader();
    return ServiceLoader.load(service, cl);
}
``````

这个静态方法的实现非常简单：

1. 获取当前线程的类加载器；
2. 调用ServiceLoader的另一个load方法

被调用load方法的实现代码如下：

``````java
public static <S> ServiceLoader<S> load(Class<S> service,
                                            ClassLoader loader)
{
    return new ServiceLoader<>(service, loader);
}
``````

实现代码还是非常的简单，具体就是new了一个ServiceLoader的实例，从而我们知道每执行一次load方法都会创建一个ServiceLoader实例，下面我们看看ServiceLoader的构造方法做了什么：

``````java
private ServiceLoader(Class<S> svc, ClassLoader cl) {
    service = Objects.requireNonNull(svc, "Service interface cannot be null");
    loader = (cl == null) ? ClassLoader.getSystemClassLoader() : cl;
    acc = (System.getSecurityManager() != null) ? AccessController.getContext() : null;
    reload();
}
``````

步骤如下：

1. 判断传入的service类是否为null；
2. 判断类加载器是否为null，为null则获取系统类加载；
3. 调用reload方法。

``````java
public void reload() {
    providers.clear();
    lookupIterator = new LazyIterator(service, loader);
}
``````

reload方法中，只是执行了providers(一个LinkedHashMap，用于缓存实现类)的清空，以及new了一个**LazyIterator**实例；**也就是说load()方法只是创建了一个ServiceLoader的实例而已，并没有执行具体的加载配置和初始化实例的事情，加载配置和初始化实例是在循环遍历的过程中执行的**。

下面我们看一下ServiceLoader的iterator方法的实现：

``````java
public Iterator<S> iterator() {
    return new Iterator<S>() {

        Iterator<Map.Entry<String,S>> knownProviders
            = providers.entrySet().iterator();

        public boolean hasNext() {
            // 首先判断providers中是否有缓存的实现类
            // 有则先遍历providers中的元素
            if (knownProviders.hasNext())
                return true;
            // 没有，则调用lookupIterator，也就是LazyIterator类中的hasNext方法
            return lookupIterator.hasNext();
        }

        public S next() {
            // 首先判断providers中是否有缓存的实现类
            // 有则先遍历providers中的元素
            if (knownProviders.hasNext())
                return knownProviders.next().getValue();
            return lookupIterator.next();
        }

        public void remove() {
            throw new UnsupportedOperationException();
        }

    };
}
``````

在遍历过程中，一开始的时候providers实例变量中肯定是没有元素的，所以我们可以看下LazyIterator中的hasNext方法做了什么：

``````java
public boolean hasNext() {
    if (acc == null) {
        return hasNextService();	
    } else {
        PrivilegedAction<Boolean> action = new PrivilegedAction<Boolean>() {
            public Boolean run() { return hasNextService(); }
        };
        return AccessController.doPrivileged(action, acc);
    }
}

// 判断是否有元素的关键方法
private boolean hasNextService() {
    if (nextName != null) {	// nextName待遍历的下一个元素的名称，初始值为null
        return true;
    }
    if (configs == null) {
        try {
            // 关键地方
            // 指定要读取的文件地址：PREFIX+service.getName()
            // PREFIX是ServiceLoader中的静态常量，值为：META-INF/services/
            // eg：service为com.shell.spi.Spi接口时
            // fullName = "META-INF/services/" + "com.shell.spi.Spi"
            String fullName = PREFIX + service.getName();
            
            // 获取资源路径 
            if (loader == null)
                configs = ClassLoader.getSystemResources(fullName);
            else
                configs = loader.getResources(fullName);
        } catch (IOException x) {
            fail(service, "Error locating configuration files", x);
        }
    }
    
    // 遍历加载资源文件
    while ((pending == null) || !pending.hasNext()) {
        if (!configs.hasMoreElements()) {
            return false;
        }
        // parse就是具体执行配置资源文件加载，并返回配置文件中的内容
        pending = parse(service, configs.nextElement());
    }
    nextName = pending.next(); // 获取值
    return true;
}
``````

ServiceLoader中的parse方法实现：

``````java
private Iterator<String> parse(Class<?> service, URL u)
        throws ServiceConfigurationError
    {
        InputStream in = null;
        BufferedReader r = null;
        ArrayList<String> names = new ArrayList<>();
        try {
        	// 获取文件资源输入流
            in = u.openStream();
            // 以utf-8的编码格式读取文件内容，所以我们的配置文件要以utf-8的方式编码，
            // 否则容易出现乱码的情况
            r = new BufferedReader(new InputStreamReader(in, "utf-8"));
            int lc = 1;
            // 读取文件中的每一行，并存放到names中
            while ((lc = parseLine(service, u, r, lc, names)) >= 0);
        } catch (IOException x) {
            fail(service, "Error reading configuration file", x);
        } finally {
            try {
                if (r != null) r.close();
                if (in != null) in.close();
            } catch (IOException y) {
                fail(service, "Error closing configuration file", y);
            }
        }
        return names.iterator();
    }
``````

LazyIterator类中的next方法：

``````java
public S next() {
    if (acc == null) {
        return nextService();
    } else {
        PrivilegedAction<S> action = new PrivilegedAction<S>() {
            public S run() { return nextService(); }
        };
        return AccessController.doPrivileged(action, acc);
    }
}

private S nextService() {
    if (!hasNextService())
        throw new NoSuchElementException();
    String cn = nextName;
    nextName = null;
    Class<?> c = null;
    try {
        // 加载类
        c = Class.forName(cn, false, loader);
    } catch (ClassNotFoundException x) {
        fail(service,
             "Provider " + cn + " not found");
    }
    // 判断加载的类是否是service的实现类
    if (!service.isAssignableFrom(c)) {
        fail(service,
             "Provider " + cn  + " not a subtype");
    }
    try {
        // 创建类的实例，并转化为接口
        S p = service.cast(c.newInstance());
        providers.put(cn, p);	// 存入缓存中
        return p;
    } catch (Throwable x) {
        fail(service,
             "Provider " + cn + " could not be instantiated",
             x);
    }
    throw new Error();          // This cannot happen
}
``````

## 四、总结

Java中通过ServiceLoader类来实现SPI，规则为在类路径META-INF/services/接口限定名文件来制定具体的实现类。在调用ServiceLoader的load方法时并不会加载配置文件，只有在遍历的时候才会加载配置文件，获取时才会实例化具体实现类，所以如果有多个实现类，那么可能我们要遍历很多个实现类才会找到们需要的实现；如果有某个实现的创建过程非常耗时，那等我们找到我们需要的实现类时，也已经耗费了一段相当长的时间了。





