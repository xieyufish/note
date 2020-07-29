# Tomcat网络模型之Acceptor

在上文对Tomcat的网络模型做了一个简单的介绍。本文开始对网络模型中涉及的组件进行介绍；本文将从如下几个方面进行分析：

1. 启动服务端套接字；
2. 接受客户端连接；

## 一、启动服务端套接字

我们知道在Tomcat中，有好几大容器：Engine、Host、Context和Wrapper以及几大组件分别是：Server、Service和Connector等；在每个容器或者组件都有其生命周期，从启动到终止；而服务器套接字的启动就是包含在其中Connector组件的启动过程中。Connector在init过程中，会启动ProtocolHandler，然后由ProtocolHandler又会触发Endpoint的启动，而服务端套接字就是在Endpoint中启动的。

在Tomcat8.0版本中，可以支持三种Endpoint，分别是：

- **NioEndpoint：**对Java中NIO的支持；
- **Nio2Endpoint：**对Java中AIO的支持；
- **AprEndpoint：**对本地库的支持，需要本地库的实现支持；

本文将以**NioEndpoint**这个实现来进行介绍。

### 1. 服务端端口绑定

在NioEndpoint的init阶段会创建一个服务端套接字，并对监听端口进行绑定，其代码实现如下：

```java
///// AbstractEndpoint.java  ///////////////////
/**
 * init定义了行为
 */
public void init() throws Exception {
    if (bindOnInit) {	// bindOnInit:是否在初始化的时候进行绑定端口的操作，默认为：true
        bind();		// 执行绑定，由子类去实现，因为不同的IO方式绑定端口的实现代码不一样
        bindState = BindState.BOUND_ON_INIT;	// 设置Endpoint的绑定状态
    }
    // 省略部分代码
}

///// NioEndpoint.java  /////////
/**
 * 实现绑定监听端口的操作
 */
@Override
public void bind() throws Exception {

    if (!getUseInheritedChannel()) { // 默认值为：false
        serverSock = ServerSocketChannel.open();	// NIO方式，打开服务端套接字通道
        /**
         * 给套接字设置属性，主要是：
         * receiveBuffer：接收缓存大小（无默认值）
         * soTimeout：accept方法的等待时长（默认值20000）
         * reuseAddress：地址重用
         */
        socketProperties.setProperties(serverSock.socket());
        // 根据地址和简体端口创建一个InetSocketAddress实例
        InetSocketAddress addr = (getAddress()!=null?new InetSocketAddress(getAddress(),getPort()):new InetSocketAddress(getPort()));
        /**
         * 将socket绑定到创建的地址实例上，java中的bind操作包含listen，所以实际这一步执行完毕客户端就可以发送连接过来了
         * getAcceptAccount()：就是设置backlog的值，默认值100（可通过acceptCount属性改变），用于控制底层连接队列的长度，意味着如果tomcat中处理连接不及时，
         * 而操作系统中的连接队列达到了100之后就会拒绝连接了，知道tomcat通过accept再取出连接为止。
         * 
         */
        serverSock.socket().bind(addr,getAcceptCount());
    } else {
        Channel ic = System.inheritedChannel();
        if (ic instanceof ServerSocketChannel) {
            serverSock = (ServerSocketChannel) ic;
        }
        if (serverSock == null) {
            throw new IllegalArgumentException(sm.getString("endpoint.init.bind.inherited"));
        }
    }
    // 设置服务端监听套接字为阻塞模式，由单独的线程来处理连接事件
    serverSock.configureBlocking(true);

    if (acceptorThreadCount == 0) {
        // 设置默认的用于处理连接事件的线程数为1，即acceptor线程数
        // 可通过acceptorThreadCount属性改变
        acceptorThreadCount = 1;
    }
    
    // 设置poller线程数（pollerThreadCount的默认值为2）
    // 可通过pollerThreadCount属性改变
    if (pollerThreadCount <= 0) {
        pollerThreadCount = 1;
    }
    // 创建一个停止门闩，在tomcat停止时用于等待poller关闭
    setStopLatch(new CountDownLatch(pollerThreadCount));

    // 如果配置了SSL，则初始化SSL的相关配置
    initialiseSsl();

    /**
     * 创建一个shared_selector（共享选择器）
     * 在内部会打开一个Selector，然后创建一个BlockPoller，这个的作用是干嘛的还不清楚，后续会补充
     */
    selectorPool.open();
}
```

***bind()***方法执行完毕之后，tomcat已经是可以接受到客户端的连接了；但是真的这些连接Tomcat是如何接收，以及通过什么方式去接收的，接下来我们看Endpoint的另一个生命阶段过程：start

### 2. 启动Acceptor

**NioEndpoint**在start过程中，会预分配一些实例和缓存空间来存放以后接收的连接；同时也会启动Acceptor和Poller线程；我们看一下它的相关代码实现：

```java
/**
 * 在生命周期函数start()中会被调用
 */
@Override
public void startInternal() throws Exception {

    if (!running) {
        running = true;	// 设置运行状态
        paused = false;

        /**
         * 预分配一个同步栈（默认大小为128），用于存放Processor的实例
         * 在Poller处理请求的时候，会从此栈中取出一个Processor实例包装请求的上下文信息，然后将Processor扔到工作线程池中
         * 由线程池调度执行完之后会将Processor实例在入栈以便下次使用
         */
        processorCache = new SynchronizedStack<>(SynchronizedStack.DEFAULT_SIZE,
                                                 socketProperties.getProcessorCache());
        /**
         * 预分配一个同步栈（默认大小128），用于存放PollerEvent实例
         * 在Acceptor将NioChannel注册到poller中时，会从此栈取PollerEvent实例来包装然后再扔到一个同步队列中去
         */
        eventCache = new SynchronizedStack<>(SynchronizedStack.DEFAULT_SIZE,
                                             socketProperties.getEventCache());
        /**
         * 预分配同步栈（128），用于存放NioChannel实例
         * 在Acceptor接收到连接之后，将其扔到Poller之前会从此栈取出NioChannel实例包装客户端socket，然后再注册到Poller
         */
        nioChannels = new SynchronizedStack<>(SynchronizedStack.DEFAULT_SIZE,
                                              socketProperties.getBufferPool());

        if ( getExecutor() == null ) {
            // 如果在配置文件中没有配置工作线程池，则创建一个默认的内部工作线程池
            createExecutor();
        }
		
        /**
         * 初始化连接数控制门闩，用于控制tomcat可以同时处理的最大连接数（默认是10000）
         */
        initializeConnectionLatch();

        // 创建Poller数组，数组大小由pollerThreadCount控制（默认2个）
        pollers = new Poller[getPollerThreadCount()];
        for (int i=0; i<pollers.length; i++) {
            pollers[i] = new Poller();	// 创建Poller实例
            // 创建poller线程
            Thread pollerThread = new Thread(pollers[i], getName() + "-ClientPoller-"+i);
            pollerThread.setPriority(threadPriority);
            pollerThread.setDaemon(true);
            pollerThread.start();	// 启动Poller线程
        }

        startAcceptorThreads();	// 创建Acceptor线程并启动，父类方法
    }
}

///// AbstractEndpoint.java ////////////
protected final void startAcceptorThreads() {
    int count = getAcceptorThreadCount();	// 获取设置的acceptorThreadCount值
    acceptors = new Acceptor[count];

    for (int i = 0; i < count; i++) {
        acceptors[i] = createAcceptor();	// 创建Acceptor实例，由子类实现
        String threadName = getName() + "-Acceptor-" + i;
        acceptors[i].setThreadName(threadName);
        Thread t = new Thread(acceptors[i], threadName);	// 创建Acceptor线程
        t.setPriority(getAcceptorThreadPriority());
        t.setDaemon(getDaemon());
        t.start();	// 启动acceptor线程
    }
}
```

在***startInternal()***方法中调用了一个***initializeConnectionLatch()***方法，这个方法在Acceptor任务中有很大的作用，我们先看一下这个方法的实现：

```java
protected LimitLatch initializeConnectionLatch() {
    /**
     * maxConnections的默认值为10000，可通过配置文件改变
     */
    if (maxConnections==-1) return null; // 如果为-1，则直接返回
    
    // 否则（即不为-1），初始化一个LimitLatch（借助AQS的一个同步实现，用于限制同时操作的数量）
    if (connectionLimitLatch==null) {
        connectionLimitLatch = new LimitLatch(getMaxConnections());
    }
    return connectionLimitLatch;
}
```

从上面的实现我们可以看到，在Endpoint的start过程中，会涉及默认线程池的创建、Poller线程的创建以及Acceptor线程的创建，本文将先关注在Acceptor线程上；在***startAcceptorThreads()***方法中，我们会根据*acceptorThreadCount*的值来创建多个Acceptor线程，也就是说：**我们可以通过配置acceptorThreadCount参数的值来控制处理连接请求的线程数量，其默认值为1。**

## 二、Acceptor线程

我们先看一下NioEndpoint实现中***createAcceptor()***方法的实现是创建的什么类型的Acceptor吧：

```java
@Override
protected AbstractEndpoint.Acceptor createAcceptor() {
    return new Acceptor();	// NioEndpoint中的内部类Acceptor
}
```

看一下Acceptor的实现代码：

```java
protected class Acceptor extends AbstractEndpoint.Acceptor {	// 继承自父类的Acceptor
    @Override
    public void run() {

        int errorDelay = 0;

        // 无限循环，知道收到关闭命令
        while (running) {

            // 收到暂停指令，则循环睡眠
            while (paused && running) {
                state = AcceptorState.PAUSED;
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    // Ignore
                }
            }

            if (!running) {
                break;	// 收到关闭命令退出无限循环
            }
            state = AcceptorState.RUNNING;	// 设置Acceptor的状态

            try {
                // 如果已经达到了最大连接数（默认10000，表示tomcat最大可同时处理的连接数），则阻塞等待，否则进行加1处理
                // 这里就是通过initializeConnectionLatch方法中创建的LimitLatch来实现的等待
                countUpOrAwaitConnection();

                SocketChannel socket = null;
                try {
					// 接收客户端连接
                    // 请注意这里，serverSock是标记为阻塞模式的，所以这里accpet方法会一直阻塞
                    // 直到有连接建立成功才会返回，同时在设置属性方法中设置的soTimeout对这个方法是没有影响的
                    socket = serverSock.accept();
                } catch (IOException ioe) {
                   	// 接收连接过程中出现了异常，减一
                    countDownConnection();
                    if (running) {
                        // 让当前处理连接的线程睡眠errorDelay毫秒之后，再抛出异常
                        // 这里睡眠的原因应该是：防止连续抛出异常，相当于缓一缓的意思
                        // errorDelay的规律：0,50,100，...，1600毫秒，每连续出现一次异常睡眠时间翻倍
                        errorDelay = handleExceptionWithDelay(errorDelay);
                        throw ioe;
                    } else {
                        break;
                    }
                }
                // 成功接收到连接，延迟时间置0
                errorDelay = 0;

                if (running && !paused) {
                    // 设置客户端socket对象的属性，以及注册到Poller中去
                    // 单独分析
                    if (!setSocketOptions(socket)) {
                        // 设置失败则关闭这个socket
                        closeSocket(socket);
                    }
                    // 进入下一次循环，接收新的请求连接
                } else {
                    // 不是运行状态了，也关闭这个socket
                    closeSocket(socket);
                }
            } catch (Throwable t) {
                // 异常处理
                ExceptionUtils.handleThrowable(t);
                log.error(sm.getString("endpoint.accept.fail"), t);
            }
        }	// 退出无限循环
        // 设置状态为结束
        state = AcceptorState.ENDED;
    }

	// 关闭socket
    private void closeSocket(SocketChannel socket) {
        countDownConnection();	// 处理数减一
        try {
            socket.socket().close();
        } catch (IOException ioe)  {
            if (log.isDebugEnabled()) {
                log.debug(sm.getString("endpoint.err.close"), ioe);
            }
        }
        try {
            socket.close();
        } catch (IOException ioe) {
            if (log.isDebugEnabled()) {
                log.debug(sm.getString("endpoint.err.close"), ioe);
            }
        }
    }
}
```

我们看一下在收到连接之后，这句代码`setSocketOptions(socket)`到底做了什么事情：

```java
protected boolean setSocketOptions(SocketChannel socket) {
    // Process the connection
    try {
        socket.configureBlocking(false);	// 设置客户端socket为非阻塞模式
        Socket sock = socket.socket();
        /**
         * 设置客户端socket的属性
         * rxBufSize：接收缓存大小
         * txBufSize：发送缓存大小
         * oobInline：紧急数据的处理方式
         * keepAlive：持久连接，注意跟Http中的持久连接区分，可以看SocketOptions.SO_KEEPALIVE的注释
         * timeout等一系列属性
         * 相关属性的意义都可以在SocketOptions接口中找到解释
         */
        socketProperties.setProperties(sock);

        // 从NioChannel栈中取出一个实例（一开始肯定是为null的），用来包装SocketChannel
        NioChannel channel = nioChannels.pop();
        if (channel == null) {
            // 创建一个Socket缓存处理器
            SocketBufferHandler bufhandler = new SocketBufferHandler(
                socketProperties.getAppReadBufSize(),
                socketProperties.getAppWriteBufSize(),
                socketProperties.getDirectBuffer());
            // new一个新的NioChannel实例
            if (isSSLEnabled()) {
                // 如果开启了SSL，则创建一个封装客户端socket的SecureNioChannel实例
                channel = new SecureNioChannel(socket, bufhandler, selectorPool, this);
            } else {
                // 否则，创建一个封装客户端socket的NioChannel实例
                channel = new NioChannel(socket, bufhandler);
            }
        } else {
            // 如果取出的实例不为空，则将新接收的客户端socket设置到这个实例上，并重置这个实例的状态
            channel.setIOChannel(socket);
            channel.reset();
        }
        /**
         * 将NioChannel实例注册到poller实例中
         * getPoller0()方法是以轮询的方式从Poller数组中取出Poller实例的
         */
        getPoller0().register(channel);
    } catch (Throwable t) {
        ExceptionUtils.handleThrowable(t);
        try {
            log.error("",t);
        } catch (Throwable tt) {
            ExceptionUtils.handleThrowable(tt);
        }
        return false;
    }
    return true;
}
```

我们再来看一下***Poller.register()***方法又做了什么：

```java
//// NioEndpoint.Poller.java //////
public void register(final NioChannel socket) {
    socket.setPoller(this);	// NioChannel与Poller绑定
    // 创建一个NioSocketWrapper包装类，用于包装NioChannel、NioEndpoint
    NioSocketWrapper ka = new NioSocketWrapper(socket, NioEndpoint.this);
    socket.setSocketWrapper(ka);	// NioChannel与NIOSocketWrapper也绑定
    ka.setPoller(this);				// NioSocketWrapper与Poller绑定
    // 设置读写的超时时间，这两个超时时间的设置没有意义，会被覆盖
    ka.setReadTimeout(getSocketProperties().getSoTimeout());
    ka.setWriteTimeout(getSocketProperties().getSoTimeout());
    // 设置keepAlive连接的限制值（默认值100）
    ka.setKeepAliveLeft(NioEndpoint.this.getMaxKeepAliveRequests());
    ka.setSecure(isSSLEnabled());	// 是否ssl
    // 重新设置读写超时时间为连接超时时间（getConnectionTimeout()方法的依然是soTimeout时间）
    ka.setReadTimeout(getConnectionTimeout());
    ka.setWriteTimeout(getConnectionTimeout());
    
    /**
     * 从PollerEvent预分配栈中取出一个PollerEvent实例（一开始是为空的）
     */
    PollerEvent r = eventCache.pop();
    ka.interestOps(SelectionKey.OP_READ);//设置感兴趣的IO事件为读（是新建立的连接，所以是读，即等待客户端发送请求过来）
    if ( r==null) 
        // 创建一个PollerEvent实例（一开始肯定是要创建的，之后这些创建的实例会扔到预分配栈里面去）
        // OP_REGISTER是Tomcat中定义的IO事件类型，表示添加到同步队列中的事件类型，表示有新的连接注册
        // PollerEvent会根据这个事件类型来决定是将客户端soket注册到Selector还是更新感兴趣的事件
        r = new PollerEvent(socket,ka,OP_REGISTER);
    else 
        r.reset(socket,ka,OP_REGISTER);
    addEvent(r);	// 将PollerEvent事件加入到Poller实例维护的一个同步队列中
}
```

至此，**Acceptor**的主要工作已经结束。用图表示其工作内容如下：

![image-20200721174609974](.\images\image-20200721222108547.png)

## 三、相关配置属性

> acceptCount

用于控制服务端套接字backLog队列的大小。当tomcat同时处理的连接数达到了maxConnections所限制的值，且backLog队列已满的情况下，客户端会收到"连接拒绝"这样的错误。也就是说tomcat可以同时接收处理的连接数应该是maxConnections+acceptCount，当某个时刻的连接数超过这个值将会被拒绝掉。

> acceptorThreadCount

控制Acceptor线程的数量，如果设置值小于等于0，会调整为默认值1。

从这个属性的值，我们知道可以同时设置多个Acceptor线程用于接收客户端连接；那么这里就可能涉及到一个常见的问题，那就是：

如果我们设置的Acceptor线程数大于1，存不存在惊群现象（惊群现象是什么请谷歌）？

分析如下：

1. 如果Tomcat的IO模式是BIO模式，则会（但是在tomcat8中已经不支持这种模式了，所以不考虑）；
2. 如果Tomcat的IO模式为NIO模式，则不会；理由不在于操作系统层面是否避免了惊群现象这个问题，而在于java中NIO实现层面本身就避免了这个问题，即在Java的NIO实现中，ServerSocketChannel.accetp()方法一次只能有一个线程在执行，如果有多个线程执行accept()方法则必须等待前一个线程释放锁才能进入到accept()方法执行，从而也就从应用层面就避免了多个线程同时去接收客户端请求的情况；
3. 如果Tomcat的IO模式为AIO模式，则会；在这种情况下就要尽量避免开始多个Acceptor线程，以免引起惊群现象。

当然，如果是在Linux平台下，其NIO和AIO的实现都是依赖操作系统的epoll机制，而epoll机制已经解决惊群现象的问题，我们也就可以不予考虑了。

> pollerThreadCount

控制Poller线程的数量，如果设置值小于等于0，会调整为1，默认值为：Math.min(2, 处理器数量)。

> socket.rxBufSize

设置每个socket的接收缓存大小，单位为字节，如果没有设置，则用JVM的默认值。

> socket.txBufSize

设置每个socket的发送缓存区大小。

> socket.soTimeout

设置读写超时时间，默认是20000毫秒（20秒）。

> socket.tcpNoDelay

设置Negal算法是否开启，意味着对缓冲区的使用方式会发生变化。默认为true

> socket.soKeepAlive

设置持久化连接，默认情况下如果一个Tcp连接在指定时间内没有活动之后系统是会关闭这个连接的，设置为true之后，会在超时之后发送一个询问包到客户端来决定是否关闭。

> socket.processorCache

设置SocketProcessorBase实例池预分配栈的最大容量；-1表示无限容量。

> socket.eventCache

设置PollerEvent实例池预分配栈的最大容量；-1表示无限容量。

> socket.bufferPool

设置NioChannel预分配栈的最大容量；-1表示无限容量。

> maxConnections

设置tomcat可以同时处理的最大的连接数量；-1表示无限制。默认值为10000。

> socket.directBuffer

在接收到连接之后，会创建一个SocketBufferHandler实例跟这个连接socket绑定，这个用于控制是否本地直接内存，表示在从socket中读取内容时是读取到DirectByteBuffer还是HeapByteBuffer中；默认值为false；建议值为true，可以减少内容拷贝的次数。

> socket.appReadBufSize

接受读内容的分配的ByteBuffer的大小，默认值8192字节。

> socket.appWriteBufSize

接受写内容的分配的ByteBuffer的大小，默认值8192字节。