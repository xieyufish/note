# Tomcat网络模型之Poller

上文我们已经介绍了Acceptor组件接收到客户端连接之后的处理逻辑；Acceptor接收到请求之后会以轮询的方式扔到Poller数组对应Poller实例中的同步队列。本文介绍的就是Poller实例从创建到处理各个IO操作的流程。

## 一、创建Poller

**Poller**实现了**Runnable**接口，上文介绍启动Poller线程我们就知道其是一个可执行体。其内部维护了一个选择器实例**Selector**对象，Poller的构造方法如下：

```java
public Poller() throws IOException {
    // 打开一个Selector实例，有关Selector的原理可以查看我这个系列的相关文章
    this.selector = Selector.open();
}
```

Poller对象与Selector是一一对应的，每个Poller实例中维护了一个Selector对象，可以用于选择对应的已经有IO就绪的socket。

## 二、IO事件同步队列

在上文中**Acceptor**会通过调用**Poller**的***register()***方法将新的连接注册到Poller的同步队列中；除了新的连接之外，其实还有事件更新时等都需要重新入队，本节会一一介绍；先让我们再认识一下***register()***这个方法吧。

```java
/**
 * 注册一个新创建的客户端socket到这个Poller中
 */
public void register(final NioChannel socket) {
    socket.setPoller(this);
    NioSocketWrapper ka = new NioSocketWrapper(socket, NioEndpoint.this);
    socket.setSocketWrapper(ka);
    ka.setPoller(this);
    ka.setReadTimeout(getSocketProperties().getSoTimeout());
    ka.setWriteTimeout(getSocketProperties().getSoTimeout());
    ka.setKeepAliveLeft(NioEndpoint.this.getMaxKeepAliveRequests());
    ka.setSecure(isSSLEnabled());
    ka.setReadTimeout(getConnectionTimeout());
    ka.setWriteTimeout(getConnectionTimeout());
    PollerEvent r = eventCache.pop();
    // 连接已经建立，所以接下来这个socket感兴趣事件应该是read（即等待客户端发送请求过来）
    // 所以这里设置的事件为OP_READ
    // 这里不会马上将这个socket注册到Poller的selector上，什么时候注册呢，下面会介绍
    ka.interestOps(SelectionKey.OP_READ);
    // 封装时的事件类型指定为OP_REGISTER（这是tomcat中定义的一个变量值）
    // 指定为OP_REGISTER的意思是让Poller在处理这个event的时候，表示需要将对应ka中的socket注册到selector上
    if ( r==null) r = new PollerEvent(socket,ka,OP_REGISTER);
    else r.reset(socket,ka,OP_REGISTER);
    addEvent(r);	// 将包装的PollerEvent加入到同步事件队列中
}
```

**addEvent()**方法除了将事件入队，其实还会做一件事：唤醒阻塞在select操作的selector，看看其代码实现：

```java
private void addEvent(PollerEvent event) {
    /**
     * events：poller中的同步无界阻塞队列
     */
    events.offer(event);	// 加入队列
    /**
     * wakeupCounter：每次有事件入队，其值会加1
     * 当wakeupCounter为-1的时候，表示执行了一个新的select()操作，且正在执行没有完成或者正在阻塞等待
     */
    if ( wakeupCounter.incrementAndGet() == 0 ) 
        // 加1之后等于0，表示有select操作正在执行或阻塞，所以我们要唤醒这个阻塞操作
        // 以便selector可以马上执行下一次select操作来处理新加入的io事件
        selector.wakeup();
}
```

除了针对新创建的客户端socket需要注册到selector之外，对于已经注册到selector的socket我们需要重新更新相关的感兴趣的io事件，这种情况下，Poller提供了一个***add()***方法，让我们可以将已经注册过的socket的感兴趣io事件再入队，代码如下：

```java
/**
 * 添加新的IO事件，就是工作线程池中的线程在处理任务的过程中需要添加新的感兴趣的io事件时调用
 */
public void add(final NioChannel socket, final int interestOps) {
    PollerEvent r = eventCache.pop();	// 先从预分配缓存中取出PollerEvent实例
    if ( r==null) 
        // 无可用再创建一个新的PollerEvent实例
        r = new PollerEvent(socket,null,interestOps);
    else 
        // 有可用，只要重置状态即可
        r.reset(socket,null,interestOps);
    addEvent(r);	// 入队
    if (close) {
        // 如果Poller已经被关闭了，则处理socket
        NioEndpoint.NioSocketWrapper ka = (NioEndpoint.NioSocketWrapper)socket.getAttachment();
        processSocket(ka, SocketEvent.STOP, false);	// 直接由当前线程处理，不会再次扔到线程池中
    }
}
```

> :warning:注意上述注册事件和添加事件都还只是把PollerEvent封装的事件类型添加到events这个同步队列中，这个时候还没有将变化同步到selector上。

## 三、执行任务

到目前为止，我们还只是把感兴趣的事件加入到同步队列中；那么什么时候把客户端socket注册到Selector上，什么时候把感兴趣事件同步到selector中去呢？又是什么时候select出已经就绪的io事件呢？

这就是Poller任务体需要完成的工作，具体代码分析如下：

```java
@Override
public void run() {
    // 无限循环，知道destroy()方法调用
    while (true) {
        boolean hasEvents = false;	// 同步队列中是否有事件
        try {
            if (!close) {
                hasEvents = events();	// 读取同步队列，同时将同步队列中的事件同步到selector上
                // 先获取wakeupCounter的值，再设置其为-1，表示在进行select操作
                if (wakeupCounter.getAndSet(-1) > 0) {
                    // wakeupCounter的值>0，说明已经有很多pollerEvent加入到了同步队列中
                    // （这些event对应的io事件在events()中已经同步到selector）
                    // 所以这里我们用的是一个selectNow()方法，马上select出已经就绪的io事件
                    keyCount = selector.selectNow();
                } else {
                    // wakeupCounter<=0，说明同步队列中没有加入新的event
                    // 所以这里用的是阻塞的select()方法，在selectorTimeout超时时间内等待io就绪事件
                    // selectorTimeout:默认时间1s
                    keyCount = selector.select(selectorTimeout);
                }
                wakeupCounter.set(0);	// select操作结束之后，设置wakeupCounter的值为0
            }
            if (close) {
                // 关闭操作
                events();	// 同步事件到selector
                timeout(0, false);	// 会关闭所有的selectionkey
                try {
                    selector.close();	// 关闭selector
                } catch (IOException ioe) {
                    log.error(sm.getString("endpoint.nio.selectorCloseFail"), ioe);
                }
                break;	// 退出循环
            }
        } catch (Throwable x) {
            ExceptionUtils.handleThrowable(x);
            log.error("",x);
            continue;
        }
        
        if ( keyCount == 0 ) 
            // 说明没有io就绪事件发生，或者是被wakeup了
            // hasEvents=false的情况，说明在上面的events()时队列中没有事件
            // 所以再次events()，表示可能在第一次events()之后加入了新的事件，然后wakeup了select
            hasEvents = (hasEvents | events());

        // keyCount>0，说明上面的select操作时有就绪的io事件，所以我们需要将发生对应事件的
        // selectionKey拿出来；keyCount<0，不做处理，直接快速的进入到下一次的循环
        Iterator<SelectionKey> iterator =
            keyCount > 0 ? selector.selectedKeys().iterator() : null;

        while (iterator != null && iterator.hasNext()) {	// 遍历所有的selectionKey
            SelectionKey sk = iterator.next();
            // 获取注册到selector时绑定的attachment对象
            // 这里很多人可能会迷惑attachment对象为什么是NioSocketWrapper类型
            // 这个在events()方法中讲Pollerevent的run()方法时你就会看到将socket注册到selector时
            // 提供的attachment对象就是NioSocketWrapper类型的
            NioSocketWrapper attachment = (NioSocketWrapper)sk.attachment();
            if (attachment == null) {
                // 为空，说明可能已经被其他线程(工作线程)给关闭了selectionKey
                iterator.remove();
            } else {
                iterator.remove();
                processKey(sk, attachment);	// 处理这个selectionKey
            }
        }

        // 处理可能的超时，会根据keyCount和hasEvents来决定如何处理
        timeout(keyCount,hasEvents);
    }// 退出无限循环

    getStopLatch().countDown();	// Poller线程门闩减一
}
```

在上述任务执行过程中，有一个关键方法***events()***，其负责将同步队列中的数据读取出来，并同步到selector上，我们看一下它的实现：

```java
public boolean events() {
    boolean result = false;

    PollerEvent pe = null;
    // 取出这个时刻为止加入到队列中的数据
    for (int i = 0, size = events.size(); i < size && (pe = events.poll()) != null; i++ ) {
        result = true;
        try {
            pe.run();	// 执行每个PollerEvent实例的run方法，会根据事件类型做相应的动作，下面分析
            pe.reset();	// 执行完run方法之后，将PollerEvent实例状态置为空
            if (running && !paused) {
                eventCache.push(pe);	// 将PollerEvent实例扔到预分配缓存中供下次使用
            }
        } catch ( Throwable x ) {
            log.error("",x);
        }
    }

    return result;	// 返回是否有数据
}
```

在***events()***方法中也没有看到与selector相关的核心代码，相关代码实现都在PollerEvent的run方法中，继续看一下：

```java
@Override
public void run() {
    if (interestOps == OP_REGISTER) {
        // interestOps是注册类型（说明是新创建的客户端socket），在创建PollerEvent的时候传入的值
        // 则把对应的客户端socket注册到selector上
        try {
            // socket.getIOChannel()获取到的就是封装在NioChannel实例socket中的SocketChannel实例
            // 通过SocketChannel实例的register方法来将socket注册到selector上
            // 感兴趣的事件类型为OP_READ，意味着等待客户端发送http请求过来
            // 注意这里的attachment参数传的值就是socketWrapper变量（NioSocketWrapper实例）
            // 这也是为什么在获取selectionKey的attachment值时可以转换为NioSocketWrapper对象的原因
            socket.getIOChannel().register(
                socket.getPoller().getSelector(), SelectionKey.OP_READ, socketWrapper);
        } catch (Exception x) {
            log.error(sm.getString("endpoint.nio.registerFail"), x);
        }
    } else {
        // 如果不是OP_REGISTER，说明是已经register过的socket更新新的感兴趣的事件
        
        // 先从selector中取出与SocketChannel关联的selectionkey对象
        final SelectionKey key = socket.getIOChannel().keyFor(socket.getPoller().getSelector());
        try {
            if (key == null) {
                // selectionkey为空，说明socket已经从selector中移除了
                // 将在处理的连接数减一，意味着可以多接收一个请求连接了
                socket.socketWrapper.getEndpoint().countDownConnection();
                ((NioSocketWrapper) socket.socketWrapper).closed = true;
            } else {
                // 否则更新感兴趣的事件
                final NioSocketWrapper socketWrapper = (NioSocketWrapper) key.attachment();
                if (socketWrapper != null) {
                    // 将原有感兴趣事件与新的事件加起来
                    int ops = key.interestOps() | interestOps;
                    socketWrapper.interestOps(ops);
                    key.interestOps(ops);	// 通过selectionkey更新，此操作就将事件同步到了selector中
                } else {
                    // attachment为空，则要通过Poller取消selectionKey
                    socket.getPoller().cancelledKey(key);
                }
            }
        } catch (CancelledKeyException ckx) {
            // 在执行key.interestOps()时可能抛出这个异常
            try {
                socket.getPoller().cancelledKey(key);
            } catch (Exception ignore) {}
        }
    }
}
```

至此，socket注册、感兴趣IO事件更新的流程都已经通畅。

## 四、处理IO

在上述流程中，当通过selector.selectNow()或者是selector.select(timeout)有获取到IO就绪事件的时候，会通过一个循环来处理所有的io就绪，其处理是通过***processKey()***方法来完成的，我们看一下这个方法的实现：

```java
protected void processKey(SelectionKey sk, NioSocketWrapper attachment) {
    try {
        if ( close ) {
            // 如果poller已经关闭，则关联的selectionkey也要取消，释放相关的资源
            cancelledKey(sk);
        } else if ( sk.isValid() && attachment != null ) {
            if (sk.isReadable() || sk.isWritable() ) {
                // 只需关注读和写这两种事件类型即可
                if ( attachment.getSendfileData() != null ) {
                    // 如果是需要发送文件，则处理文件，由Poller线程直接处理了
                    processSendfile(sk,attachment, false);
                } else {
                    // 将已经发生的就绪事件从感兴趣事件里面移除
                    unreg(sk, attachment, sk.readyOps());
                    
                    boolean closeSocket = false;
                    if (sk.isReadable()) {
                        // 可读事件（即客户端发送了数据过滤）
                        if (!processSocket(attachment, SocketEvent.OPEN_READ, true)) {
                            closeSocket = true;	// 处理可读事件失败就关闭socket
                        }
                    }
                    if (!closeSocket && sk.isWritable()) {
                        // 可写事件（即服务端有数据需要发送给客户端）
                        if (!processSocket(attachment, SocketEvent.OPEN_WRITE, true)) {
                            closeSocket = true;	// 处理可写事件失败关闭socket
                        }
                    }
                    if (closeSocket) {
                        cancelledKey(sk);	// 关闭socket，释放资源
                    }
                }
            }
        } else {
            //selectionkey不是valid，或者是绑定attachment为空了，也要取消selectionkey释放资源
            cancelledKey(sk);
        }
    } catch ( CancelledKeyException ckx ) {
        cancelledKey(sk);
    } catch (Throwable t) {
        ExceptionUtils.handleThrowable(t);
        log.error("",t);
    }
}
```

### 1. 处理读写

由***processKey()***方法可知其处理io的核心会引用到***processSocket()***这个方法，也就是说最终还是由这个方法来决定如何处理io事件，看代码实现，你会发现这个方法会将io事件封装成**SocketProcessorBase**对象，将其扔到工作线程池中去处理，实现如下：

```java
/**
 * socketWrapper保留了相关的上下文信息
 * event表示事件类型，除了读写之外，tomcat还定义了其他几类事件
 * dispath表示是否将任务扔到线程池中去处理
 */
public boolean processSocket(SocketWrapperBase<S> socketWrapper,
                             SocketEvent event, boolean dispatch) {
    try {
        if (socketWrapper == null) {
            return false;
        }
        SocketProcessorBase<S> sc = processorCache.pop();	// 从预分配缓存中取一个实例出来
        if (sc == null) {
            // 预分配缓存中没有可用实例，则新建一个，包装了socketWrapper和event事件类型
            sc = createSocketProcessor(socketWrapper, event);
        } else {
            // 否则，重置取出实例的状态即可
            sc.reset(socketWrapper, event);
        }
        Executor executor = getExecutor();	// 获取到线程池对象
        if (dispatch && executor != null) {
            executor.execute(sc);	// 分发到线程池中执行
        } else {
            sc.run();	// 否则直接在当前线程执行
        }
    } catch (RejectedExecutionException ree) {
        // 加入线程池被拒绝
        getLog().warn(sm.getString("endpoint.executor.fail", socketWrapper) , ree);
        return false;
    } catch (Throwable t) {
        // 其他异常，比如创建线程由于内存不足失败啊等等
        ExceptionUtils.handleThrowable(t);
        getLog().error(sm.getString("endpoint.process.fail"), t);
        return false;
    }
    return true;	// 到了这里说明任务已经放到了线程池成功或者是执行成功了
}
```

所以，现在的关键其实就是**SocketProcessorBase**是如何完成任务的，SocketProcessorBase会将任务移交给协议处理器执行，协议处理器再转交给Processor去处理，所以最终的业务逻辑执行会通过Processor来触发处理。

### 2.文件处理

文件处理是由***processSendfile()***方法来完成的，其实现如下：

```java
/**
 * calledByProcessor: 表示是否由工作线程池中的线程调用Processor时触发的调用
 */
public SendfileState processSendfile(SelectionKey sk, NioSocketWrapper socketWrapper,
                                     boolean calledByProcessor) {
    NioChannel sc = null;
    try {
        unreg(sk, socketWrapper, sk.readyOps());	// 首先将io就绪事件从感兴趣事件中注销
        SendfileData sd = socketWrapper.getSendfileData();	// 获取待发文件的信息

        if (sd.fchannel == null) {
            // 打开文件
            File f = new File(sd.fileName);
            @SuppressWarnings("resource") // Closed when channel is closed
            FileInputStream fis = new FileInputStream(f);
            sd.fchannel = fis.getChannel();
        }

        // 获取NioChannel
        sc = socketWrapper.getSocket();
        // TLS/SSL连接处理有些许不同
        WritableByteChannel wc = ((sc instanceof SecureNioChannel)?sc:sc.getIOChannel());

        if (sc.getOutboundRemaining()>0) {
            // TLS/SSL连接可能会走这里，普通连接一定不会走这分支
            // 如果在缓存中还有待发送数据
            if (sc.flushOutbound()) {
                socketWrapper.updateLastWrite();
            }
        } else {
            // 缓存中没有待发数据
            // 普通连接一定会走这分支
            
            // 通过文件通道将文件内容传输到wc这个可写的socket中去
            // 关于transferTo拷贝可以参考我之前的文章
            long written = sd.fchannel.transferTo(sd.pos,sd.length,wc);
            if (written > 0) {
                sd.pos += written;
                sd.length -= written;	// 记录还有多少内容没有发送完毕
                socketWrapper.updateLastWrite();	// 更新最后一次写操作的时间
            } else {
                // 传送文件失败了，一般情况下不会出现这个问题
                // 所以检查一下sendfiledada中指定的文件pos与文件大小的对应关系看是不是这个问题引起的失败
                if (sd.fchannel.size() <= sd.pos) {
                    throw new IOException("Sendfile configured to " +
                                          "send more data than was available");
                }
            }
        }
        if (sd.length <= 0 && sc.getOutboundRemaining()<=0) {
            // 文件内容发送完毕
            socketWrapper.setSendfileData(null);	// 将sendfile信息置空
            try {
                sd.fchannel.close();// 关闭文件资源
            } catch (Exception ignore) {
            }

            if (!calledByProcessor) {
                // 发送文件完毕之后，处理相关事件
                switch (sd.keepAliveState) {
                    case NONE: {
						// 连接不需要保活，直接关闭回收相关资源
                        close(sc, sk);
                        break;
                    }
                    case PIPELINED: {
						// 连接需要保活，且在输入缓存中还有管道数据待读
                        // 则继续处理待读数据
                        if (!processSocket(socketWrapper, SocketEvent.OPEN_READ, true)) {
                            close(sc, sk);
                        }
                        break;
                    }
                    case OPEN: {
                        // 连接需要保活，重新添加新的感兴趣读事件（意味着继续等待客户端的下一次请求）
                        reg(sk,socketWrapper,SelectionKey.OP_READ);
                        break;
                    }
                }
            }
            return SendfileState.DONE;	// 返回文件处理完毕
        } else {
			// 文件内容没有发送完毕，可能是只发送了部分甚至还没开始发送文件内容
            if (calledByProcessor) {
                // 如果是被Processor调用处理的，则重新入队（为什么要这样呢？）
                add(socketWrapper.getSocket(),SelectionKey.OP_WRITE);
            } else {
                // 如果是poller线程直接发送的文件，则直接更新写事件
                reg(sk,socketWrapper,SelectionKey.OP_WRITE);
            }
            return SendfileState.PENDING;	// 返回文件还待处理状态
        }
    } catch (IOException x) {
        if (!calledByProcessor && sc != null) {
            close(sc, sk);
        }
        return SendfileState.ERROR;
    } catch (Throwable t) {
        if (!calledByProcessor && sc != null) {
            close(sc, sk);
        }
        return SendfileState.ERROR;
    }
}
```

### 3. 注销socket

在poller关闭、selectionkey非法等情况下，我们都需要将注册到selector上的客户端socket从注册列表中移除，此操作是通过***cancelledKey()***方法来实现的，代码如下：

```java
/**
 * selectionkey维护了selector和socketchannel之间的关系，所以通过它我们能找到相关的信息
 */
public NioSocketWrapper cancelledKey(SelectionKey key) {
    NioSocketWrapper ka = null;
    try {
        if ( key == null ) return null;
        ka = (NioSocketWrapper) key.attach(null);	// 获取绑定的attachment对象的同时将这个属性置空
        if (ka != null) {
            // 调用协议处理器来释放与socketwrapper管理的Processor处理器
            getHandler().release(ka);
        }
        // 调用cancel方法，最终会调用selector的cancel方法，将selectionkey加入到cancelled队列中
        // 关于cancelled队列的处理可以看之前的文章
        if (key.isValid()) key.cancel();

        if (ka != null) {
            try {
                ka.getSocket().close(true);	// 关闭客户端socketchannel资源
            } catch (Exception e){
                
            }
        }

        if (key.channel().isOpen()) {
            try {
                key.channel().close();	// 关闭socketchannel资源
            } catch (Exception e) {
                
            }
        }
        try {
            if (ka != null && ka.getSendfileData() != null
                && ka.getSendfileData().fchannel != null
                && ka.getSendfileData().fchannel.isOpen()) {
                // 如果有sendfile数据，且打开的文件没有关闭，需要关闭文件资源
                ka.getSendfileData().fchannel.close();
            }
        } catch (Exception ignore) {
        }
        if (ka != null) {
            countDownConnection();	// 正在处理的连接数要减一
            ka.closed = true;	// 设置socketwrapper的关闭状态
        }
    } catch (Throwable e) {
        ExceptionUtils.handleThrowable(e);
        if (log.isDebugEnabled()) log.error("",e);
    }
    return ka;	// 返回绑定的attachment对象
}
```

### 4. 超时处理

```java
protected void timeout(int keyCount, boolean hasEvents) {
    long now = System.currentTimeMillis();
    if (nextExpiration > 0 && (keyCount > 0 || hasEvents) && (now < nextExpiration) && !close) {
        // 如果select出了就绪事件且还没有到下一次检查超时的时间，且没有关闭，则没有必要处理超时
        return;
    }
    
    // 超时处理
    int keycount = 0;
    try {
        for (SelectionKey key : selector.keys()) { // 遍历所有注册到selector上的socket
            keycount++;
            try {
                NioSocketWrapper ka = (NioSocketWrapper) key.attachment();
                if ( ka == null ) {
                    // 对于任何没有绑定attachment的都要取消掉
                    cancelledKey(key);
                } else if (close) {
                    // 如果已经关闭，则注销感兴趣的事件
                    key.interestOps(0);
                    ka.interestOps(0);
                    processKey(key,ka);	// 内部也是调用的cancelledKey方法
                } else if ((ka.interestOps()&SelectionKey.OP_READ) == SelectionKey.OP_READ ||
                           (ka.interestOps()&SelectionKey.OP_WRITE) == SelectionKey.OP_WRITE) {
                    // 注册的事件是读或者写
                    
                    boolean isTimedOut = false; // 判断是否超时
                    if ((ka.interestOps() & SelectionKey.OP_READ) == SelectionKey.OP_READ) {
                        long delta = now - ka.getLastRead();
                        long timeout = ka.getReadTimeout();
                        // 距离最后一次读操作的时间是否超过了设置的读超时时间
                        isTimedOut = timeout > 0 && delta > timeout;
                    }
					// 如果读没有超时，在判断写是否超时
                    if (!isTimedOut && (ka.interestOps() & SelectionKey.OP_WRITE) == SelectionKey.OP_WRITE) {
                        long delta = now - ka.getLastWrite();
                        long timeout = ka.getWriteTimeout();
                        // 距离最后一次写操作的时间是否超过了设置的写超时时间
                        isTimedOut = timeout > 0 && delta > timeout;
                    }
                    if (isTimedOut) {
                        // 如果确实超时了
                        // 注销io事件
                        key.interestOps(0);
                        ka.interestOps(0);
                        ka.setError(new SocketTimeoutException());	// 设置错误异常
                        // 超时的处理（会将socket关闭掉，最终也是通过cancelledKey()方法来完成的）
                        // 奇怪，这个为什么不传：SocketEvent.TIMEOUT这样的状态值呢？
                        if (!processSocket(ka, SocketEvent.ERROR, true)) {
                            // 处理失败，则需要取消selectionkey
                            cancelledKey(key);
                        }
                    }
                }
            }catch ( CancelledKeyException ckx ) {
                cancelledKey(key);
            }
        }
    } catch (ConcurrentModificationException cme) {
        log.warn(sm.getString("endpoint.nio.timeoutCme"), cme);
    }
    long prevExp = nextExpiration;
    nextExpiration = System.currentTimeMillis() +
        socketProperties.getTimeoutInterval();	// 用于检查是否超时的间隔时间（默认为1s）
}
```

## 五、可配置参数

> selectorTimeout

配置select操作的阻塞时间，默认值为1000毫秒。这个值设置的短一些，可以相应的减少唤醒操作所带来的时间消耗；但是设置的太短也可能会造成select的无用功。

> socket.soTimeout

控制在接收到一个TCP连接之后，多长时间内没有接收到客户端发送数据，即关闭这个连接；默认为20000毫秒；判断超时的时候不会由这个值来严格决定，还与**timeoutInterval**这个值有关，这个值的默认时间为1秒，且不可变更，所以严格来说默认情况，如果服务端接收到一个TCP连接之后，如果在20秒的内没有发送信息可能不会被关闭，21秒还没有发送信息是一定要被关闭的。

注意这个参数是和connectionTimeout一样功能的。

## 六、Poller工作流程图

![image-20200724111033467](images\image-20200729231237628.png)