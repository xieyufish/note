# Java对异步I/O的实现之网络异步I/O

从面向的不同对象来说，I/O分为两个大的方面：文件I/O和网络I/O。异步I/O也一样分为异步文件I/O和异步网络I/O。针对这两个方面，Java都提供了各自的实现来提供对这两方面的支持，本文主要介绍的是Java中对异步网络I/O的支持实现。

讲到网络，不可避免的要涉及到**socket**这个标准的网络接口；在Java中，普通的阻塞网络I/O就不可避免的要涉及到**ServerSocket**和**Socket**；针对异步网络I/O，Java在**其1.7版本的NIO包中**也提供了配套的**AsynchronousServerSocketChannel**和**AsynchronousSocketChannel**分别来代表服务端和客户端，除了这两个关键抽象类之外，Java中还提供了一个关键抽象类**AsynchronousChannelGroup**用于对异步网络I/O的支持。其他的还有：**AsynchronousChannelProvider**、**ThreadPool**等。

## 一、AsynchronousChannelProvider抽象类

从名字我们就可以看出，这是一个异步通道提供器。属于*java.nio.channels.spi*包下，用于提供具体的实例对象。抽象类也意味着还有具体的实现，***其具体实现是跟系统平台相关的，比如Windows系统下的实现是WindowsAsynchronousChannelProvider，Linux系统下的实现是LinuxAsynchronousChannelProvider***；本文还不会涉及具体的平台，所以只会介绍**AsynchronousChannelProvider**这个抽象类的功能，主要如下：

> 维护一个AsynchronousChannelProvider单例，由这个单例来提供各种类型异步通道的实例对象；

代码如下：

```java
/**
 * 获取提供器单例
 * 从实现可以看到是通过一个静态内部类的方式来实现单例创建的（保证安全性和懒加载）
 */
public static AsynchronousChannelProvider provider() {
    return ProviderHolder.provider;
}
```

看一下**ProviderHolder**这个内部类是怎么定义***provider***的：

```java
static final AsynchronousChannelProvider provider = load();	// 通过load方法来加载实例

private static AsynchronousChannelProvider load() {
    return AccessController
        .doPrivileged(new PrivilegedAction<AsynchronousChannelProvider>() {
            public AsynchronousChannelProvider run() {
                AsynchronousChannelProvider p;
                p = loadProviderFromProperty();	// 通过系统属性java.nio.channels.spi.AsynchronousChannelProvider指定的值来加载
                if (p != null)
                    return p;
                p = loadProviderAsService();	// 通过SPI机制来加载
                if (p != null)
                    return p;
                return sun.nio.ch.DefaultAsynchronousChannelProvider.create(); // 利用默认的方式来创建，这个默认实现是平台相关的，不同的操作系统有不同的实现
            }});
}
```

> 提供用于创建各种类型的异步通道实例的方法。

代码如下：

```java
/**
 * 创建一个AsynchronousChannelGroup类型的实例，其包含线程池实例的线程数是固定的
 * nThreads：线程池中最大的线程数
 * threadFactory：用于创建线程实例的工厂类
 */
public abstract AsynchronousChannelGroup openAsynchronousChannelGroup(int nThreads, ThreadFactory threadFactory) throws IOException;

/**
 * 创建一个AsynchronousChannelGroup类型的实例，其包含线程池实例的线程数是不固定的可扩展的
 * initialSize：线程池中的初始线程数
 * executor：执行器
 */
public abstract AsynchronousChannelGroup openAsynchronousChannelGroup(ExecutorService executor, int initialSize) throws IOException;

/**
 * 创建一个AsynchronousServerSocketChannel类型的实例，即封装了服务端监听Socket的一个异步通道实例，用于服务端
 * group：AsynchronousServerSocketChannel实例需要绑定的一个异步通道组，为null的时候，Java虚拟机会创建一个默认的异步通道组实例并将这个通道绑定到这个默认的组上
 */
public abstract AsynchronousServerSocketChannel openAsynchronousServerSocketChannel(AsynchronousChannelGroup group) throws IOException;

/**
 * 创建一个AsynchronousSocketChannel类型的实例，即封装了客户端Socket的一个异步通道实例，用于客户端
 * group：AsynchronousSocketChannel实例需要绑定到一个异步通道组，为null的时候，Java虚拟机会创建一个默认的异步通道组实例并将这个通道绑定到这个默认的组上
 */
public abstract AsynchronousSocketChannel openAsynchronousSocketChannel(AsynchronousChannelGroup group) throws IOException;
```

从方面定义也都可以看到，只是提供了一个方法声明，具体的实现由平台提供。这一点通过我们机器上安装的java环境我们也可以看到，在jdk/jre/lib目录下的rt.jar文件中我们也可以看到有具体的实现类字节码class文件。之所以不同系统要提供不同的实现是因为每个系统对于异步I/O的实现机制是不一样的，比如Windows是通过Iocp来实现的异步I/O，Unix系的系统是通过Port机制来实现的异步I/O等等。

## 二、AsynchronousChannelGroup抽象类

一个共享资源的异步通道组。在其内部封装了处理由绑定在当前组的**AsynchronousChannel**实例发出的已完成I/O操作的机制。在一个异步通道组的内部维护了一个线程池，这个线程池负责处理提交给它的I/O事件任务并在这些事件完成的时候负责将这些任务结果分发到**CompletionHandler**进行消费。

### 1. 创建方式

一个异步通道组可以通过调用如下三个静态方法来创建：

> withFixedThreadPool()静态方法

```java
/**
 * 创建一个包含固定大小线程池的异步通道组，其内部就是通过调用AsynchronousChannelProvider对应方法来实现的。
 * nThreads：线程池中的线程数
 * threadFactory：线程创建工厂类
 */
public static AsynchronousChannelGroup withFixedThreadPool(int nThreads, ThreadFactory threadFactory)
    throws IOException
{
    return AsynchronousChannelProvider.provider()
        .openAsynchronousChannelGroup(nThreads, threadFactory);
}
```

> withCachedThreadPool()静态方法

```java
/**
 * 利用已有的executor创建一个包含可变线程数线程池的异步通道组。
 * executor：线程池
 * initialSize：线程池中的初始化线程数
 */
public static AsynchronousChannelGroup withCachedThreadPool(ExecutorService executor, int initialSize)
    throws IOException
{
    return AsynchronousChannelProvider.provider()
        .openAsynchronousChannelGroup(executor, initialSize);
}
```

> withThreadPool()静态方法

```java
/**
 * 利用已有的executor创建一个可变线程数线程池的异步通道组。
 * executor：线程池
 */
public static AsynchronousChannelGroup withThreadPool(ExecutorService executor)
    throws IOException
{
    return AsynchronousChannelProvider.provider()
        .openAsynchronousChannelGroup(executor, 0);
}
```

通过上面三种方式我们可以精确的创建出一个异步通道组，在创建异步通道实例的时候我们可以传入自己创建的异步通道组来让异步通道绑定到我们自己创建的异步通道组上。同时，Java虚拟机也提供了一个默认的异步通道组，如果在创建异步通道时没有显示指定异步通道组，那么Java会将这个异步通道绑定到这个默认的异步通道组。Java虚拟机提供了两个系统属性，让我们定义这个默认的异步通道组，分别如下：

| 属性名                                            | 描述                                                         |
| ------------------------------------------------- | ------------------------------------------------------------ |
| java.nio.channels.DefaultThreadPool.threadFactory | 线程工厂类的全路径名，用于创建线程。必须是java.util.concurrent.ThreadFactory的子类 |
| java.nio.channels.DefaultThreadPool.initialSize   | 默认线程池的初始化大小                                       |

### 2. 关闭和终止

两种关闭异步通道组的方式：

> shutdown()：命令式关闭

命令式关闭首先会标记异步通道组的关闭状态；此时如果还有构建的异步通道绑定到这个异步通道组实例就会抛出异常**ShutdownChannelGroupException**。调用了***shutdown()***方法之后，异步通道组并不会马上终止，而是会等到所有绑定其上的异步通道关闭、所有正在执行的CompletionHandler执行结束、以及所有相关资源被释放之后才会进入终止状态；可以通过调用***isTerminated()***来判断是否进入了终止状态；调用***isShutdown()***方法可以判断是否进入关闭状态；调用***awaitTermination()***可以阻塞等待异步通道组终止。

> shutdownNow()：强制关闭

强制关闭除了标记关闭状态外，会将所有绑定到这个组的异步通道关闭，即调用他们的***close()***方法。

### 3. 实现类：AsynchronousChannelGroupImpl

在Java的sun包下提供了一个平台不相关的**AsynchronousChannelGroup**类的基础抽象实现**AsynchronousChannelGroupImpl**，具体的平台实现都是继承自这个基础实现来完成具体的异步工作的。现在我们来看看一个基础实现做了些什么工作。

#### 关键属性

> ThreadPool pool

维护的一个线程池，就是对ExecutorService的包装；在创建的时候赋值初始化。

> Queue<Runnable> taskQueue

一个任务队列，只有在线程池是固定大小的时候才会使用（即通过withFixedThreadPool方法创建异步通道组实例的时候）。

> ScheduledThreadPoolExecutor timeoutExectutor

一个超时处理线程池。

> int internalThreadCount

内部线程数，当使用无界线程池时才会使用（即通过withCachedThreadPool方法创建异步通道组实例的时候），表示用于处理I/O事件的内部线程数。默认值为：1。

> AtomicInteger threadCount

表示当前正在运行的任务数。

#### 关键方法

> 构造函数

```java
AsynchronousChannelGroupImpl(AsynchronousChannelProvider provider,
                                 ThreadPool pool)
{
    super(provider);
    this.pool = pool;	// 赋值线程池，线程池创建是在外部创建的（是在具体的Provider实现类中创建的）

    if (pool.isFixedThreadPool()) {	// 线程池是固定大小模式的时候初始化任务队列
        taskQueue = new ConcurrentLinkedQueue<Runnable>();
    } else {
        taskQueue = null;
    }

    // 创建一个定时超时线程池
    this.timeoutExecutor = (ScheduledThreadPoolExecutor)
        Executors.newScheduledThreadPool(1, ThreadPool.defaultThreadFactory());
    this.timeoutExecutor.setRemoveOnCancelPolicy(true);
}
```

> startThreads

```java
/**
 * 启动线程，会根据线程池大小（或初始大小）启动所有的线程，这里启动的所有线程池会用于去监听异步IO完成事件
 * 也就意味着：
 * 1. 针对固定线程数大小的线程池，其所有的线程都会去监听异步IO完成事件，监听到之后也是由这个线程池线程来处理完成任务；
 * 2. 针对Cached线程数的线程池，其监听异步IO事件的有[internalThreadCount个单独的线程 + initialSize个池内线程]，监听到之后任务只能由池内线程处理
 *    internalThreadCount个线程不会去处理完成任务只负责监听和将任务扔到池中
 */
protected final void startThreads(Runnable task) {
    if (!isFixedThreadPool()) {	// 不是固定线程数的线程池，即属于无界线程池（CachedThreadPool）
        for (int i=0; i<internalThreadCount; i++) {	// 循环创建并启动内部线程，默认值为1，不归线程池管理（在CachedThreadPool类型非常有用）
            startInternalThread(task);	// 启动线程
            threadCount.incrementAndGet();
        }
    }
    if (pool.poolSize() > 0) {	// 线程池内的线程启动，poolSize就是nThreads（FixedThreadPool）或者initialSize（CachedThreadPool）的值
        task = bindToGroup(task);	// 将当前异步通道组实例绑定到线程本地变量表中，后续在处理异步IO完成事件的就是通过是否绑定来决定任务该不该扔线程池来处理的
        try {
            // 根据线程池初始大小启动池内线程
            // poolSize()在固定大小线程池中即为固定的线程数值，在无界线程池中即为初始化线程数
            for (int i=0; i<pool.poolSize(); i++) {
                pool.executor().execute(task);
                threadCount.incrementAndGet();
            }
        } catch (RejectedExecutionException  x) {
            // nothing we can do
        }
    }
}
```

> bindToGroup

```java
private Runnable bindToGroup(final Runnable task) {
    final AsynchronousChannelGroupImpl thisGroup = this; // 保存当前实例（异步通道组实例）
    return new Runnable() {
        public void run() {
            Invoker.bindToGroup(thisGroup);	// 将当前实例绑定到线程本地变量表中
            task.run();
        }
    };
}
```

> shutdown和shutdownNow

```java
@Override
public final void shutdown() {
    if (shutdown.getAndSet(true)) { // 设置关闭状态
        // 已经关闭直接返回
        return;
    }
    
    // 如果当前异步通道组中还有异步通道，则直接返回；直到最后一个异步通道实例关闭
    if (!isEmpty()) {
        return;
    }
    // 设置终止状态，并关闭完成处理器任务和线程池
    synchronized (shutdownNowLock) {
        if (!terminateInitiated) {
            terminateInitiated = true;
            shutdownHandlerTasks();		// 关闭所有等待I/O事件的任务，由具体的平台提供实现
            shutdownExecutors();		
        }
    }
}

@Override
public final void shutdownNow() throws IOException {
    shutdown.set(true);	// 设置关闭状态
    // 在强制关闭命令中不会检查当前是否还有异步通道绑定，直接设置终止并关闭所有的异步通道
    synchronized (shutdownNowLock) {
        if (!terminateInitiated) {
            terminateInitiated = true;	// 设置终止状态
            closeAllChannels();			// 关闭所有的异步通道，由具体平台提供实现
            shutdownHandlerTasks();		// 关闭所有等待I/O事件的任务，由具体的平台提供实现
            shutdownExecutors();
        }
    }
}
```

> execute

```java
/**
 * 实现Executor中的方法
 * 用异步通道组中的线程池中的线程来执行给定的任务
 */
@Override
public final void execute(Runnable task) {
    SecurityManager sm = System.getSecurityManager();
    if (sm != null) {
        final AccessControlContext acc = AccessController.getContext();
        final Runnable delegate = task;
        task = new Runnable() {
            @Override
            public void run() {
                AccessController.doPrivileged(new PrivilegedAction<Void>() {
                    @Override
                    public Void run() {
                        delegate.run();
                        return null;
                    }
                }, acc);
            }
        };
    }
    executeOnPooledThread(task);
}
```

> executeOnPooledThread

```java
/**
 * 针对固定大小的线程池，将任务添加提交到队列中，并唤醒等待I/O事件的发生的线程来执行这个任务
 * 针对其他类型的线程池，只是简单的提交任务到线程池中
 */
final void executeOnPooledThread(Runnable task) {
    if (isFixedThreadPool()) {
        executeOnHandlerTask(task);	// 由具体的平台提供实现
    } else {
        pool.executor().execute(bindToGroup(task)); // 提交到线程池中去执行
    }
}
```

## 三、AsynchronousServerSocketChannel抽象类

面向流（即TCP协议）的侦听套接字（即服务端使用的套接字）的异步通道。可通过如下两个重载方法来创建一个这样的实例：

```java
/**
 * 通过异步通道组实例，利用异步通道提供器来创建一个异步通道，会将异步通道绑定到这个组
 */
public static AsynchronousServerSocketChannel open(AsynchronousChannelGroup group)
        throws IOException
{
    AsynchronousChannelProvider provider = (group == null) ?
        AsynchronousChannelProvider.provider() : group.provider(); // 获取创建这个组的服务提供器
    return provider.openAsynchronousServerSocketChannel(group);		// 通过服务提供者创建异步通道，上面有讲
}

/**
 * 创建一个异步通道，并将其绑定到默认的异步通道组（由Java虚拟机自己提供）
 */
public static AsynchronousServerSocketChannel open()
        throws IOException
{
    return open(null);	// 调用的上面的方法
}
```

跟**ServerSocket**和**ServerSocketChannel**这些服务端的侦听套接字对象一样，在**AsynchronousServerSocketChannel**中，也只提供如下方法，用于完成服务端套接字该有的功能：

1. bind()：将套接字绑定到本地某个端口，并启动监听；
2. accept()：接收客户端连接；
3. getLocalAddress()：获取套接字绑定的端口地址；
4. setOption()和getOption()：选项操作。

有一点点区别的是，在**AsynchronousServerSocketChannel**中提供了重载的两个***accept()***方法，用于支持我们在上篇文章中提到过的异步操作支持的两种使用方式，***accept()***方法的定义如下：

```java
/**
 * CompletionHandler处理方式
 */
public abstract <A> void accept(A attachment, CompletionHandler<AsynchronousSocketChannel,? super A> handler);

/**
 * Future方式
 */
public abstract Future<AsynchronousSocketChannel> accept();
```

跟**AsynchronousChannelGroup**一样，Java中也提供了一个基础抽象实现类**AsynchronousServerSocketChannelImpl**继承自**AsynchronousServerSocketChannel**，更具体的实现也是更操作系统平台相关的，不属于本文所要讲的内容，暂时略过。在这个基础抽象实现类中维护了一个套接字类型的文件描述符。其构造方法如下：

```java
AsynchronousServerSocketChannelImpl(AsynchronousChannelGroupImpl group) {
	super(group.provider());
	this.fd = Net.serverSocket(true);	// 创建一个TCP类型的套接字并保存到属性fd中，通过本地代码调用系统接口来创建一个socket类型的文件描述符对象
}
```

在这个基础实现类中，对***accept()***方法的实现如下：

```java
@Override
public final Future<AsynchronousSocketChannel> accept() {
    return implAccept(null, null);	// 调用的implAccept方法
}

@Override
@SuppressWarnings("unchecked")
public final <A> void accept(A attachment, CompletionHandler<AsynchronousSocketChannel,? super A> handler)
{
    if (handler == null)	// CompletionHandler处理器不能为空
        throw new NullPointerException("'handler' is null");
    // 也是调用的impleAccept方法
    implAccept(attachment, (CompletionHandler<AsynchronousSocketChannel,Object>)handler);
}

/**
 * 从上面两个实现方法来看，最终都是通过调用这个implAccept方法来实现的
 * 本方法是个抽象定义，其具体的实现由平台提供
 */
abstract Future<AsynchronousSocketChannel> implAccept(Object attachment, CompletionHandler<AsynchronousSocketChannel,Object> handler);
```

至于其他的***bind()***、***setOption()***、***getOption()***跟普通的I/O方式没有什么区别，这里就不再赘述了；感兴趣的可以查看我之前的文章。

从整个**AsynchronousServerSocketChannelImpl**实现来看，除了在构造函数中用到异步通道组这个东西以外，好像其他地方我们都没看着这个异步通道组对象启了什么作用，以及异步通道是如何和异步通道组进行关联的我们好像也没看到任何头绪。其实这一块东西在更具体的平台实现中处理，在基础抽象实现中并不关心这些。

## 四、AsynchronousSocketChannel抽象类

面向流（即TCP）的连接套接字（即客户端套接字）的异步通道。跟服务端异步通道一样，也有两个重载方式来创建一个客户端异步通道，代码如下：

```java
public static AsynchronousSocketChannel open() throws IOException
{
    return open(null);
}

/**
 * 通过异步通道组，利用异步通道服务提供器来创建一个客户端异步通道，并这个异步通道绑定到传入的异步通道组上面
 */
public static AsynchronousSocketChannel open(AsynchronousChannelGroup group) throws IOException
{
    AsynchronousChannelProvider provider = (group == null) ?
        AsynchronousChannelProvider.provider() : group.provider();
    return provider.openAsynchronousSocketChannel(group);
}
```

跟其他两种类型的客户端套接字一样，异步通道客户端套接字也提供了如下客户端套接字需要的能力：

1. bind：将套接字绑定到某个本地端口；
2. connect：连接服务端指定的套接字地址；
3. read：从连接中读取内容；
4. write：将内容写入连接中；
5. 其他的支持方法：比如option方法、获取远端地址的方法、关闭读、关闭写等。

在异步通道中提供的这些I/O操作方法跟其他两种类型不同的是提供了两种重载方法，用于支持异步操作的两种不同操作：CompletionHandler方式和Future方式。在这里不再贴源码出来。

同服务端异步通道一样，Java也提供了一个基础抽象实现**AsynchronousSocketChannelImpl**，用于完成基本功能，具体的实现也是跟具体的平台相关，不同平台会提供更具体的实现类。我们看一下基础抽象实现类做了什么？

```java
AsynchronousSocketChannelImpl(AsynchronousChannelGroupImpl group) throws IOException
{
    super(group.provider());
    this.fd = Net.socket(true);	// 创建一个TCP套接字，最终会调用到内核层面代码来分配一个socket类型的文件描述符及相关内存块
    this.state = ST_UNCONNECTED;
}

// 重载的构造方法，用已有的文件描述创建一个对象，不会再新建socket套接字
AsynchronousSocketChannelImpl(AsynchronousChannelGroupImpl group,
                              FileDescriptor fd,
                              InetSocketAddress remote)
    throws IOException
{
    super(group.provider());
    this.fd = fd;
    this.state = ST_CONNECTED;
    this.localAddress = Net.localAddress(fd);
    this.remoteAddress = remote;
}
```

读、写和连接这三个I/O操作，最终都会调用到下面三个方法：

```java
abstract <A> Future<Void> implConnect(SocketAddress remote,
                                          A attachment,
                                          CompletionHandler<Void,? super A> handler);

/**
 * 不管是scattering类型的读还是当个ByteBuffer的读，最终都是通过这个方法来实现
 * isScatteringRead：当前是scattering类型读还是其他类型的读，这个参数会控制读内容是写入dst还是写入dsts
 */
abstract <V extends Number,A> Future<V> implRead(boolean isScatteringRead,
                                                     ByteBuffer dst,
                                                     ByteBuffer[] dsts,
                                                     long timeout,
                                                     TimeUnit unit,
                                                     A attachment,
                                                     CompletionHandler<V,? super A> handler);

/**
 * 不管是gathering类型的写还是其他类型的写，最终都是通过这个方法来实现
 * isGatheringWrite：当前是gathering类型的写还是其他类型的写，这个参数会控制是将src还是srcs中的内容写出去
 */
abstract <V extends Number,A> Future<V> implWrite(boolean isGatheringWrite,
                                                      ByteBuffer src,
                                                      ByteBuffer[] srcs,
                                                      long timeout,
                                                      TimeUnit unit,
                                                      A attachment,
                                                      CompletionHandler<V,? super A> handler);
```

这三个方法的具体实现都是由平台提供具体实现的，不同平台实现不同。至于其他的方法，比如bind、shutdownInput、shutdownOutput等方法都跟阻塞I/O里面差不多，这里也不再赘述。

## 五、总结

本文主要介绍的是Java层提供的异步IO的实现，其实通篇看下来再Java层面也还是只提供了一些抽象实现，把一些基本的功能实现提供了，但是具体的异步如何实现操作的由于跟具体的平台相关，不同平台实现异步的机制不一样，所以在本文也无法更详细的涉及。

跟平台相关的异步实现，将会分两篇不同文章讲述，敬请期待。