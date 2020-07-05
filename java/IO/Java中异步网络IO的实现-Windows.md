# Java中网络异步I/O之Windows系统下的实现

在上篇文章中，我们讲解了Java中对异步IO的抽象实现；更具体的由于平台实现机制的区别是有平台来提供实现的。本文将聚焦在Windows平台对异步IO的支持-IOCP的原理及Java层面在windows平台上的具体实现。

## 一、认识IOCP

IOCP(I/O completion ports)，即**I/O完成端口**，Windows平台的专有实现。I/O完成端口在多处理机系统上为众多的异步IO请求提供了一个高效的线程处理模型。操作系统给每个I/O完成端口分配一个**队列**，用于服务异步IO请求；并通过与一个**预分配的线程池**合作可以高效且快速的处理许多并发的异步IO请求。其基本原理如下图：

![image-20200701165824244](.\images\image-20200701165824244.png)

> :warning:**文件句柄**：Windows中的文件句柄是一个系统抽象概念，代表的是一个重叠I/O端点，可以理解为文件描述符，它不仅可以代表打开的文件，还可以表示TCP socket、命名管道、网络端点等等。

上图是我通过阅读Windows提供的开发文档，根据自己理解所画出来的，可能在一些细节或者是数据结构上跟最终的实现有差距，请各位读者注意分辨。

### 1. IOCP提供的系统接口

Windows的库文件中提供了若干接口，让程序员可以操作IOCP来实现异步IO功能。本文将粗略的介绍Java中用到的几个接口。主要如下：

> **CreateIoCompletionPort**
>
> 方法参数：
> HANDLE  FileHandle：支持重叠I/O的文件句柄，如果值为INVALID_HANDLE_VALUE，则方法会创建一个新的完成端口，且此种情况下，ExistingCompletionPort必须为NULL，同时会忽略CompletionKey参数。
> HANDLE  ExistingCompletionPort：一个已经存在的完成端口；为NULL时会创建一个新的完成端口，并将FileHandle与完成端口关联；
> ULONG_PTR  CompletionKey：用户定义的值，每个IO Completion Packet通过这个值可以关联到FileHandle
> DWORD  NumberOfConcurrentThreads：并行度，系统允许的可以同时用于处理IO Completion Packet的最大线程数

这个接口完成两个功能：

1. 创建一个I/O完成端口；
2. 将指定的文件句柄与I/O完成端口关联起来。

这两个功能都是根据传入参数值来完成的，如果传入的参数值已经指定了I/O完成端口值则不会再创建；如果传入的文件句柄对象不为NULL，就将这个文件句柄与I/O完成端口建立关联关系。这个接口会接收一个并行度参数，这个参数决定了可以同时运行的工作线程数。

>  **GetQueuedCompletionStatus**
>
> 方法参数：
> HANDLE  CompletionPort：完成端口
> LPDWORD  lpNumberOfBytesTransferred：指向一个变量的指针，这个变量值表示的是I/O操作完成之后可以传输的数据大小（字节数）
> PULONG_PTR  lpCompletionKey：指向一个变量的指针，这个变量值表示的是已完成I/O操作的文件句柄的关联的Completion Key
> LPOVERLAPPED  *lpOverlapped：指向一个OVERLAPPED结构起始地址的指针，OVERLAPPED存放的就是I/O操作的结果
> DWORD  dwMilliseconds：毫秒值，表示调用者准备等待Completion Packet出现的时长；为INFINITE表示一直等待，为0表示不等待

如果FIFO队列中有元素，则出队一个Completion Packet；否则等待与I/O完成端口关联的正在进行的I/O操作完成。这个方法可以通过设置一个超时时间值来决定是否等待以及等待的时长。如果想一次出队多个Completion Packet，可以使用**GetQueuedCompletionStatusEx**接口。

> **PostQueuedCompletionStatus**
>
> 方法参数：
> HANDLE  CompletionPort：完成端口
> DWORD  dwNumberOfBytesTransferred：指定GetQueuedCompletionStatus函数将会返回的lpNumberOfBytesTransferred值的大小
> ULONG_PTR  dwCompletionKey：指定GetQueuedCompletionStatus函数将会返回的lpCompletionKey的值
> LPOVERLAPPED  lpOverlapped：指定GetQueuedCompletionStatus函数将会返回的lpOverlapped的值

发送一个Completion Packet到I/O完成端口上，主要用于自定义事件。

Windows还提供了其他支持异步操作的接口函数，比如：**WSASend**、**WSARecv**等一系列支持异步读写的接口函数，这里就不再列举。感兴趣的可以查看Windows提供的开发文档进行查看。

### 2. IOCP的工作流程

![image-20200701171452568](.\images\image-20200701171452568.png)

> :warning:IOCP涉及多个线程，其执行流程并不是一个严格的线性关系，上图只是从宏观层面粗略的展示一个大体的流程，请各位读者注意。

流程描述：

1. 首先在主线程中，通过调用**CreateIoCompletionPort**接口来新建一个**I/O完成端口**，系统会为这个新建的完成端口分配一个**FIFO队列**；
2. 根据新建完成端口是指定的并行度大小，启动相应的工作线程数，在每个线程中调用接口**GetQueuedCompletionStatus**进入阻塞等待的状态（进入等待状态的线程会进入一个栈结构中，按后进先出的顺序处理I/O完成）；
3. 创建文件句柄（这里是指创建网络socket），并将文件句柄与刚刚创建的I/O完成端口关联起来（在关联的时候有一个重要的参数***CompletionKey***，这个参数会绑定到文件句柄上，在I/O事件完成时可以根据这个值找到对应的文件句柄对象）；
4. 文件句柄发出异步I/O事件（连接事件、读事件、写事件）；
5. 异步I/O事件完成之后会生成一个**Completion Packet**结构，并将这个结构加入到FIFO队列中；
6. 异步I/O事件完成之后会唤醒等待中的工作线程（后进先出的规则），工作线程处理这个I/O完成事件之后，继续调用**GetQueuedCompletionStatus**进入等待或者获取下一个I/O完成事件。

当一个线程第一次调用**GetQueuedCompletionStatus**时，就会和I/O完成端口关联起来，直到这个线程关闭、指定另一个完成端口或者是完成端口关闭。

### 3. IOCP线程池与并行度

**并行度**是I/O完成端口中非常重要的需要认真考虑的一个属性；这个属性值在调用**CreateIoCompletionPort**接口创建完成端口时指定。这个属性值会限制关联到完成端口上的线程的可运行数量；当关联到完成端口上的处于可运行状态线程数达到这个值的时候，后续的关联线程调用将被阻塞，直到运行数下降。

并行度的设计要考虑这样的一种情况：当工作线程一个一个进入等待状态入栈的之后，如果Completion Packet此时源源不断的进入FIFO队列，并且栈顶的线程可以及时处理完这个Completion Packet就有可能造成早入栈的线程饿死一直等不到执行的机会。所以，一般建议设置并行度的大小为处理器个数。

> :warning:并行度控制的是**可同时运行的线程数**；不包括等待线程数在内。

下面我们将结合上文所讲内容，正式进入Java世界对IOCP的封装处理是如何做的。

## 二、Java对IOCP的封装实现

在上文我们已经讲过Java层面对异步IO支持的抽象及其抽象基础实现。那么跟平台具体实现相关的又是怎么样的呢？

### 1. DefaultAsynchronousChannelProvider

我们说过Java中会通过**AsynchronousChannelProvider**服务提供器来提供具体的异步通道相关实例，在其***provider()***方法中会依次通过系统属性、SPI及默认提供器创建器来创建服务提供器；而默认提供器创建器是平台相关的实现，下面看其在Windows平台下的实现，代码如下：

```java
public class DefaultAsynchronousChannelProvider {
    private DefaultAsynchronousChannelProvider() { }

    public static AsynchronousChannelProvider create() {
        return new WindowsAsynchronousChannelProvider();	// 创建一个Windows平台的异步通道提供器
    }
}
```

意味着在Windows平台下，通过***AsynchronousChannelProvider.provider()***方法返回的异步通道提供器最终是一个**WindowsAsynchronousChannelProvider**实例。

### 2. WindowsAsynchronousChannelProvider

**AsynchronousChannelProvider**抽象类的具体实现，我们来看一下它是如何提供异步通道相关的各种实例的，实现代码如下：

```java
public class WindowsAsynchronousChannelProvider extends AsynchronousChannelProvider {
    // 默认的Iocp实例，是AsynchronousChannelGroupImpl的实现
    // 在创建异步通道时传入的异步通道组为null时使用
    private static volatile Iocp defaultIocp;

    // 默认构造方法
    public WindowsAsynchronousChannelProvider() {
    }

    // 为默认Iocp赋值，使用了非常经典的单例设计模式（懒加载方式）
    private Iocp defaultIocp() throws IOException {
        if (defaultIocp == null) {
            synchronized (WindowsAsynchronousChannelProvider.class) {
                if (defaultIocp == null) {
                    // 创建Iocp实例，并启动所有工作线程
                    defaultIocp = new Iocp(this, ThreadPool.getDefault()).start();
                }
            }
        }
        return defaultIocp;
    }

    /**
     * 创建固定数量线程的异步通道组Iocp
     */
    @Override
    public AsynchronousChannelGroup openAsynchronousChannelGroup(int nThreads, ThreadFactory factory)
        throws IOException
    {
        /**
         * 1. 首先通过ThreadPool的静态方法create创建一个Fixed类型的线程池
         *    （方法内部通过Executors.newFixedThreadPool()创建线程池，线程数固定为nThreads）；
         * 2. 然后new一个Iocp类型的实例；
         * 3. 调用Iocp的start方法启动nThreads个工作线程。
         */
        return new Iocp(this, ThreadPool.create(nThreads, factory)).start();
    }

    /**
     * 根据executor线程数量可变的异步通道组Iocp
     */
    @Override
    public AsynchronousChannelGroup openAsynchronousChannelGroup(ExecutorService executor, int initialSize)
        throws IOException
    {
        /**
         * 1. 首先通过ThreadPool的静态方法wrap创建一个nonFixed类型的线程池
         *    （方法内部会判断executor的类型是否为ThreadPoolExecutor类型且是否是cached线程池，
         *     如果是，则重设initialSize的值，如果传入的initialSize<0，则取处理器个数值；否则取0）
         * 2. 如果executor不是ThreadPoolExecutor类型，且initialSize<0，则将initialSize重设为0；
         * 3. new一个Iocp类型实例
         * 4. 调用Iocp的start方法启动[initialSize + internalThreadCount（默认值为1）]个工作线程。
         */
        return new Iocp(this, ThreadPool.wrap(executor, initialSize)).start();
    }

    /**
     * 将创建异步通道时传入的异步通道组对象转换为Iocp类型（因为是windows平台）
     */
    private Iocp toIocp(AsynchronousChannelGroup group) throws IOException {
        if (group == null) {
            return defaultIocp();
        } else {
            if (!(group instanceof Iocp))	// 如果不是Iocp类型实例，抛出异常
                throw new IllegalChannelGroupException();
            return (Iocp)group;
        }
    }

    /**
     * 通过异步通道组来创建一个Windows版本的服务端异步通道实例
     */
    @Override
    public AsynchronousServerSocketChannel openAsynchronousServerSocketChannel(AsynchronousChannelGroup group)
        throws IOException
    {
        return new WindowsAsynchronousServerSocketChannelImpl(toIocp(group));
    }

    /**
     * 通过异步通道组来创建一个Windows版本的客户端异步通道实例
     */
    @Override
    public AsynchronousSocketChannel openAsynchronousSocketChannel(AsynchronousChannelGroup group)
        throws IOException
    {
        return new WindowsAsynchronousSocketChannelImpl(toIocp(group));
    }
}
```

### 3. Iocp

从上面的**WindowsAsynchronousChannelProvider**实现类，我们已经知道了Windows平台下创建的异步通道组是**Iocp**类的实例，**Iocp**类在*sun.nio.ch*包下，继承自**AsynchronousChannelGroupImpl**这个抽象实现类。**Iocp**类是对Windows中Iocp的封装实现，在此类中维护了如下几个关键属性：

> long **port**

指向底层IO完成端口的句柄值，根据它可以访问到内核的IO完成端口对象。

> Map<Integer, OverlappedChannel> **keyToChannel**

CompletionKey到Channel的映射关系，在IO操作完成之后，底层会根据Completion Packet获取到CompletionKey，在Java应用层可以通过这个映射关系找到对应的通道对象，从而完成后续的处理。

:warning:**CompletionKey**是Java中指定的值，并不是由内核分配的，在第一部分接口介绍的时候也说过这个值是有用户指定的。

> Set<Long> **staleIoSet**

过期的OVERLAPPED结构。

下面看一下，**Iocp**类中的几个关键方法，看它如何实现对底层Iocp的调用的。

> **构造函数**

```java
/**
 * 唯一构造函数，传入一个服务提供器对象和一个线程池对象
 */
Iocp(AsynchronousChannelProvider provider, ThreadPool pool) throws IOException
{
    // 调用父类构造方法，在上文说过线程池对象由AsynchronousChannelGroupImpl维护
    super(provider, pool);
    // 创建IO完成端口，由port属性引用
    this.port = createIoCompletionPort(INVALID_HANDLE_VALUE, 0, 0, fixedThreadCount());
    this.nextCompletionKey = 1;	// 下次待使用的CompletionKey的值
}
```

Iocp中构造函数的实现很简单，核心步骤在于调用***createIoCompletionPort***方法来创建一个**IO完成端口**，这是一个本地方法，其定义如下：

```Java
/**
 * handle：文件句柄值，本次调用传入的INVALID_HANDLE_VALUE（意味着是要创建一个新的IO完成端口）
 * existingPort：已经存在的完成端口值，本次调用传入0值（转换到本地代码之后就是NULL，也是意味着创建新的完成端口）
 * completionKey：completionKey，本次调用传入0，在本次调用这个值会被忽略（后续在唤醒线程时也是用的0）
 * concurrency：并行度，用于控制IO完成端口允许的最大运行线程数，本次通过fixedThreadCount()函数来取值，
 * 			   1. 对于fixed类型的线程池，这个值就是nThreads值
 *             2. 对于非fixed类型的线程池，这个值就是initialSize + internalThreadCount
 */
private static native long createIoCompletionPort(long handle,
        long existingPort, int completionKey, int concurrency) throws IOException;
```

可以看到，这个本地方法的定义和本文第一部分介绍Iocp中的接口**CreateIoCompletionPort**其实是一样的，只不过参数类型都换成了Java中的类型；不错，这个本地方法的实现其实很简单，就是调用的**CreateIoCompletionPort**这一个接口，本地实现代码如下：

```c++
JNIEXPORT jlong JNICALL
Java_sun_nio_ch_Iocp_createIoCompletionPort(JNIEnv* env, jclass this,
    jlong handle, jlong existingPort, jint completionKey, jint concurrency)
{
    ULONG_PTR ck = completionKey;
    // 调用系统提供的接口函数CreateIoCompletionPort
    HANDLE port = CreateIoCompletionPort((HANDLE)jlong_to_ptr(handle), // 将handle转换为HANDLE对象
                                         (HANDLE)jlong_to_ptr(existingPort), // 将existingPort转换为HANDLE对象（0->NULL）
                                         ck,
                                         (DWORD)concurrency);
    if (port == NULL) {	// 创建失败，抛出异常
        JNU_ThrowIOExceptionWithLastError(env, "CreateIoCompletionPort failed");
    }
    return ptr_to_jlong(port);
}
```

在服务提供器代码中，创建异步通道组实例的时候，是以`new Iocp(...).start()`这样的方式实现的，那下面来看看***start()***做了什么。

> **start()**

```java
Iocp start() {
    startThreads(new EventHandlerTask()); // 调用父类的startThreads方法
    return this;	// 还是返回当前实例
}
```

***start()***方法的实现也很简单，调用父类AsynchronousChannelGroupImpl中的***startThreads()***方法并返回当前实例就结束了。***startThreads()***方法在上文讲过，主要就是启动线程池内nThreads个线程来执行Task，忘了的同学可以去回顾一下。那这里的核心就剩下**EventhandlerTask**类了，这个类是用了完成什么任务呢，我们可以看一下：

```Java
private class EventHandlerTask implements Runnable {
    public void run() {
        Invoker.GroupAndInvokeCount myGroupAndInvokeCount =
            Invoker.getGroupAndInvokeCount();	// 获取当前线程本地变量表中的Iocp实例
        boolean canInvokeDirect = (myGroupAndInvokeCount != null);
        CompletionStatus ioResult = new CompletionStatus();	// 存放IO操作完成结果
        boolean replaceMe = false;

        try {
            for (;;) {	// 无限循环
                if (myGroupAndInvokeCount != null)
                    myGroupAndInvokeCount.resetInvokeCount();	// 重置调用次数

                replaceMe = false;
                try {
                    // 等待IO操作完成事件（一直等待）
                    // 反应到底层就是等待FIFO队列中的Completion Packet
                    getQueuedCompletionStatus(port, ioResult);	// 本地方法
                } catch (IOException x) {
                    x.printStackTrace();
                    return;
                }

                /**
                 * 线程唤醒或者是关闭操作
                 * 如果线程执行到了这，说明线程已经等到了一个Completion Packet
                 * 如果Completion Packet对应的completionKey=0并且overlapped=0，说明这是一个自定义唤醒（指通过调用wakeup()来唤醒）操作或者是关闭操作（也会调用wakeup()）
                 * 唤醒操作是通过发送一个自定义的模拟消息到FIFO队列来实现的，具体在wakeup()方法会介绍
                 */
                if (ioResult.completionKey() == 0 &&
                    ioResult.overlapped() == 0L)
                {
                    // 唤醒线程的目的：用于处理IO操作完成之后相应的业务逻辑任务，
                    // 因为这里监听IO完成事件的线程也是属于线程池中的线程，而有些CompletionHandler任务也是要扔到池中取执行的，
                    // 所以可能存在当CompletionHandler扔到池中时，所有线程都处于等待IO完成事件的状态，这个时候就需要唤醒线程来执行CompletionHandler任务
                    // 所以这里会先从任务队列中获取任务，taskQueue属于AsynchronousChannelGroupImpl中维护的队列，只有在fixed类型线程池才会有
                    Runnable task = pollTask();
                    if (task == null) {	// 任务为空，说明没有需要处理的任务，或者已经被处理了，则线程执行结束
                        return;
                    }

                    // 任务不为空，执行任务
                    // 任务执行完毕之后，继续进入等待IO完成事件的状态
                    replaceMe = true;
                    task.run();
                    continue;
                }

                /**
                 * 到这里说明线程等到的Completion Packet对应的CompletionKey != 0或者overlapped != 0
                 * 意味着是某个文件句柄（socket）对应的异步IO操作完成了
                 * 这个时候我们要根据对应的CompletionKey值取到对应的Channel实例
                 */
                OverlappedChannel ch = null;
                keyToChannelLock.readLock().lock();
                try {
                    ch = keyToChannel.get(ioResult.completionKey());
                    if (ch == null) {	// 对应的通道不存在了，可能是已经关闭了
                        checkIfStale(ioResult.overlapped());
                        continue;
                    }
                } finally {
                    keyToChannelLock.readLock().unlock();
                }

                // 根据overlapped值获取对应的发出这个I/O请求的封装了相关信息的Future对象
                PendingFuture<?,?> result = ch.getByOverlapped(ioResult.overlapped());
                if (result == null) {
                    checkIfStale(ioResult.overlapped());
                    continue;
                }

                // 如果I/O操作完成任务已经执行完毕了（被发出I/O操作的线程执行完毕或直接设置了完成态），则继续循环
                synchronized (result) {
                    if (result.isDone()) {
                        continue;
                    }
                }

                // 调用I/O操作结果处理器
                int error = ioResult.error();
                ResultHandler rh = (ResultHandler)result.getContext();
                replaceMe = true;
                if (error == 0) {	// 如果I/O操作正常完成，最终会调用到CompletionHandler
                    rh.completed(ioResult.bytesTransferred(), canInvokeDirect);
                } else {	// I/O操作异常完成，最终会调用到CompletionHandler
                    rh.failed(error, translateErrorToIOException(error));
                }
            }
        } finally {
            // 到了这里，说明已经退出了循环，线程已经不需要做任务了
            // 首先关闭当前线程
            // 然后判断当前关闭的线程是否最后一个（remaining=0）
            // 如果是最后一个关闭线程且当前是关闭状态，则执行关闭操作来关闭Iocp（关闭底层完成端口以及释放占用的资源）
            int remaining = threadExit(this, replaceMe);
            if (remaining == 0 && isShutdown()) {
                implClose();
            }
        }
    }
}
```

从**EventHandlerTask**的实现，其主要任务便是调用本地方法**getQueuedCompletionStatus**等待IO完成事件，并将IO操作结果分发到结果处理器**ResultHandler**（ResultHandler是一个接口，至于具体的实现做了什么在后续介绍）。我们继续看一下本地方法**getQueuedCompletionStatus**的定义吧，看看它的本地实现：

```java
/**
 * completionPort：IO完成端口，这里传入的是Iocp中port这个属性值
 * status：CompletionStatus实例，用于接收异步IO操作的结果值，
 *        其包含有：completionKey、error、overlapped、bytesTransferred属性值（对应到第一部分介绍系统接口的参数）
 */
private static native void getQueuedCompletionStatus(long completionPort,
        CompletionStatus status) throws IOException;
```

本地实现是通过调用接口**GetQueuedCompletionStatus**实现的，代码如下：

```c++
JNIEXPORT void JNICALL
Java_sun_nio_ch_Iocp_getQueuedCompletionStatus(JNIEnv* env, jclass this,
    jlong completionPort, jobject obj) // obj就是传进来的status实例对象
{
    DWORD bytesTransferred;
    ULONG_PTR completionKey;
    OVERLAPPED *lpOverlapped;
    BOOL res;

    res = GetQueuedCompletionStatus((HANDLE)jlong_to_ptr(completionPort),
                                  &bytesTransferred,
                                  &completionKey,
                                  &lpOverlapped,
                                  INFINITE); // INFINITE表示一直等待，知道有completion packet
    if (res == 0 && lpOverlapped == NULL) { // 出现异常
        JNU_ThrowIOExceptionWithLastError(env, "GetQueuedCompletionStatus failed");
    } else {
        // 将bytesTransferred、completionKey、lpOverlapped、error状态设置到传入的CompletionStatus对象上
        DWORD ioResult = (res == 0) ? GetLastError() : 0;
        (*env)->SetIntField(env, obj, completionStatus_error, ioResult);
        (*env)->SetIntField(env, obj, completionStatus_bytesTransferred,
            (jint)bytesTransferred);
        (*env)->SetIntField(env, obj, completionStatus_completionKey,
            (jint)completionKey);
        (*env)->SetLongField(env, obj, completionStatus_overlapped,
            ptr_to_jlong(lpOverlapped));

    }
}
```

至此，Iocp创建IO完成端口及工作线程监听IO完成事件的工作都已经完成，那么文件句柄（socket）是怎么关联到IO完成端口及解除关联的呢？

> **associate**

用于将指定的文件句柄和IOCP关联起来，并在**Iocp**类中保存一个对应的映射关系，代码分析如下：

```java
/**
 * ch：通道实例，这里传入的应该是WindowsAsynchronousServerSocketChannelImpl或者是WindowsAsynchronousSocketChannelImpl的实例，
 * handle：文件句柄，在创建WindowsAsynchronousServerSocketChannelImpl或者是WindowsAsynchronousSocketChannelImpl实例时创建的
 */
int associate(OverlappedChannel ch, long handle) throws IOException {
    keyToChannelLock.writeLock().lock();	// 获取一个写锁

    int key;
    try {
        if (isShutdown())	// Iocp（异步通道组）已经执行了关闭命令，就不允许再建立关联了
            throw new ShutdownChannelGroupException();

        // 生成一个新的completionkey
        do {
            key = nextCompletionKey++;
        } while ((key == 0) || keyToChannel.containsKey(key));

        // 将文件句柄与完成端口port关联起来
        if (handle != 0L) {
            // 本地方法，上面有介绍
            // 注意这里调用的区别在于参数值的不一样
            createIoCompletionPort(handle, port, key, 0);
        }

        // 将completionkey与Channel的映射关系保存起来
        keyToChannel.put(key, ch);
    } finally {
        keyToChannelLock.writeLock().unlock();	// 释放写锁
    }
    return key;
}
```

关联操作是由具体的异步通道实例在创建的过程中发起的。

> **disassociate**

在java层面解除异步通道与异步通道组的绑定关系，代码分析如下：

```java
/**
 * key：CompletionKey值
 */
void disassociate(int key) {
    boolean checkForShutdown = false;

    keyToChannelLock.writeLock().lock();
    try {
        keyToChannel.remove(key); // 直接移除

        if (keyToChannel.isEmpty())	// 如果是异步通道组绑定的最后一个异步通道
            checkForShutdown = true;

    } finally {
        keyToChannelLock.writeLock().unlock();
    }

    // 如果执行了关闭命令且是最后一个解除绑定关系的异步通道，则马上关闭异步通道组
    if (checkForShutdown && isShutdown()) {
        try {
            shutdownNow();
        } catch (IOException ignore) { }
    }
}
```

:warning:这里解除的只是异步通道与Iocp实例的绑定关系，在底层这个关联关系此时是还没有解除的，因此在Java层面调用了**disassociate**函数之后，应调用Chanel的close方法来关闭通道已解除底层的关联关系。

> **executeOnHandlerTask**

这是**AsynchronousChannelGroupImpl**中定义的方法，在介绍***executeOnPooledThread***方法时有提到过，用于在fixed类型的线程池下，将任务加入队列并唤醒相关线程来处理任务队列，代码分析如下：

```java
@Override
void executeOnHandlerTask(Runnable task) {
    synchronized (this) {
        if (closed)
            throw new RejectedExecutionException();
        offerTask(task);	// 将任务加入到任务队列中
        wakeup();	// 唤醒正在等待IO完成事件的线程用于处理任务
    }

}
```

> **wakeup**

唤醒等待IO完成事件的线程，代码实现如下：

```java
private void wakeup() {
    try {
        postQueuedCompletionStatus(port, 0); // 本地方法，用于往完成端口发送一个自定义消息来激活处理等待中的线程
    } catch (IOException e) {
        // should not happen
        throw new AssertionError(e);
    }
}
```

***postQueuedCompletionStatus***方法定义如下：

```java
/**
 * completionPort：完成端口，这里传入的是port属性值，向这个端口发送Completion Packet
 * completionKey：这里传入0，在通过GetQueuedCompletionStatus获取结果时，
 *                将获取到这里传入的completionKey值，从而在被激活线程中可以通过completionKey的值来判断线程是被自定义唤醒的还是被特性的IO完成事件唤醒的
 */
private static native void postQueuedCompletionStatus(long completionPort,
        int completionKey) throws IOException;
```

毫无疑问，跟上面提到的两个本地方法一样，这个本地方法内部肯定也是调用的**PostQueuedCompletionStatus**接口函数来实现的，具体代码如下：

```c++
JNIEXPORT void JNICALL
Java_sun_nio_ch_Iocp_postQueuedCompletionStatus(JNIEnv* env, jclass this,
    jlong completionPort, jint completionKey)
{
    BOOL res;

    res = PostQueuedCompletionStatus((HANDLE)jlong_to_ptr(completionPort),
                                     (DWORD)0, // dwNumberOfBytesTransferred的值为0，意味着通过GetQueuedCompletionstatus获取到的numberOfBytesTransferred的值也是0
                                     (DWORD)completionKey, // Java传入的completionKey值
                                     NULL); // lpOverlapped值为NULL，意味着通过GetQueuedCompletionstatus获取到的lpOverlapped值也为NULL（转换到Java中的值会变成0）
    if (res == 0) {
        JNU_ThrowIOExceptionWithLastError(env, "PostQueuedCompletionStatus");
    }
}
```

### 4. WindowsAsynchronousServerSocketChannelImpl

**WindowsAsynchronousServerSocketChannelImpl**是Java在Windows平台下提供的服务端异步通道实现，继承自**AsynchronousServerSocketChannelImpl**，同时实现了**Iocp**类的内部接口**OverlappedChannel**。本类也属于*sun.nio.ch*包下的实现。下面我们来分析一下这个实现类。

#### 关键属性

> long **handle**

文件句柄，指向底层的服务端socket，是通过父类的**fd**属性获取的。

> int **completionKey**

跟当前实例关联的completionKey值，在当前实例跟Iocp进行关联的时候获得。

> Iocp **iocp**

记录当前通道关联到的Iocp实例。

> PendingIoCache **ioCache**

用于缓存PendingFuture（这里的话主要是存放Accept类型的Future任务），overlapped到PendingFuture实例的一个映射关系。一般情况下这个缓存大小是0或者是1，在PendingIoCache中也维护了一个OVERLAPPED结构的缓存（维护的这个缓存好像并没有用于存放具体的数据，好像只在映射关系起到了作用）。

> long **dataBuffer**

用于缓存跟客户端socket相关的本地地址和远程地址信息，通过Unsafe分配的一个本地内存，它的大小由**DATA_BUFFER_SIZE(88)**控制。

> AtomicBoolean **accepting**

用于标记当前是否已经发出过一个异步accept命令。

#### 关键函数

> 构造函数

```java
WindowsAsynchronousServerSocketChannelImpl(Iocp iocp) throws IOException {
    super(iocp);	// 调用父类构造函数，传入Iocp异步通道组实例

    // 通过父类的fd对象获取到其内部封装的文件句柄（文件描述符）
    long h = IOUtil.fdVal(fd);
    int key;
    try {
        // 调用iocp的associate方法，将当前异步通道实例绑定到iocp组中，同时将文件句柄关联完成端口，并返回completionKey值
        // associate方法解析见上文
        key = iocp.associate(this, h);
    } catch (IOException x) {
        closesocket0(h);   // 出现异常就关闭已经创建的底层socket回收资源，防止内存泄露
        throw x;
    }

    // 初始化各属性值
    this.handle = h;
    this.completionKey = key;
    this.iocp = iocp;
    this.ioCache = new PendingIoCache();
    this.dataBuffer = unsafe.allocateMemory(DATA_BUFFER_SIZE); // 分配指定大小的本地内存，并将首地址记录下来
}
```

> **getByOverlapped(long overlapped)**

实现自**Iocp.OverlappedChannel**接口的函数，用于根据overlapped的值（在IO操作完成的时候会获取到这个值）来取到对应的PendingFuture对象，从而可以通过PendingFuture来执行IO完成工作。代码实现分析如下：

```java
@Override
public <V,A> PendingFuture<V,A> getByOverlapped(long overlapped) {
    return ioCache.remove(overlapped); 	// 从ioCache属性中取出PendingFuture，并从缓存中移除（存放进去的时机是在发出accept命令之前）
}
```

> **implAccept(Object attachment, CompletionHandler handler)**

实现自**AsynchronousServerSocketChannelImpl**类（在上文讲这个类的时候，我们讲过重载的两个***accept()***方法最终都是通过调用抽象方法***implAccept()***来完成的）中的方法，下面我们来看一下异步通道是如何来实现接收一个客户端请求的。代码分析如下：

```java
@Override
Future<AsynchronousSocketChannel> implAccept(Object attachment,
                                             final CompletionHandler<AsynchronousSocketChannel,Object> handler)
{
    if (!isOpen()) {
        // 如果当前通道已经处于关闭状态，则初始化一个关闭异常
        Throwable exc = new ClosedChannelException();
        if (handler == null)	// 判断完成处理器是否为空，来确定是哪种异步操作方式
            return CompletedFuture.withFailure(exc);	// 为空，返回一个Future实例
        
        // 否则通过调用CompletionHandler完成处理器实例来处理
        // invokeIndirectly方式是指不是直接通过当前线程来处理结果，而是把这个handler封装成一个任务扔到iocp的线程池去处理
        Invoker.invokeIndirectly(this, handler, attachment, null, exc);
        return null;
    }
    if (isAcceptKilled())
        // isAcceptKilled：是否杀死了accept，是则抛出异常
        // 发生场景：
        // 在调用accept之后返回了一个Future类型实例（PendingFuture），然后在这个Future实例的任务完成之前调用了这个Future实例的cancel方法会造成isAcceptKilled返回true
        
        // 这就意味着，虽然我们没有直接调用通道的关闭方法，而只是调用了其一个任务的cancel方法也有可能造成通道不再处理接收连接的任务
        // 建议在调用Future的cancel之后，应该要在调用Iocp.shutdown命令来命令式关闭，已释放所有的资源
        throw new RuntimeException("Accept not allowed due to cancellation");

    if (localAddress == null)
        // 还没有绑定本地端口，抛出异常
        throw new NotYetBoundException();

    WindowsAsynchronousSocketChannelImpl ch = null;
    IOException ioe = null;
    try {
        begin();
        // 创建一个客户端异步通道，在创建的时候也会绑定到iocp上，具体在文章后续讲解
        ch = new WindowsAsynchronousSocketChannelImpl(iocp, false);
    } catch (IOException x) {
        ioe = x;
    } finally {
        end();
    }
    if (ioe != null) {
        if (handler == null)
            return CompletedFuture.withFailure(ioe);
        Invoker.invokeIndirectly(this, handler, attachment, null, ioe);
        return null;
    }

    // 获取一个资源权限访问控制器上下文用于处理资源访问权限的问题
    AccessControlContext acc = (System.getSecurityManager() == null) ?
        null : AccessController.getContext();

    /*********************************************************
     ****************** 重点 *******  *************************
     * 利用当前通道实例、完成处理器实例handler、以及attachment三个属性创建一个PendingFuture实例
     * 这个PendingFuture其实是保存了一个上下文的环境，在accept这个io操作完成的时候可以根据overlapped的值
     * 获取到这个PendingFuture，从而可以根据其保存的环境来处理相关完成任务。
     */
    PendingFuture<AsynchronousSocketChannel,Object> result =
        new PendingFuture<AsynchronousSocketChannel,Object>(this, handler, attachment);
    // 利用客户端异步通道实例、权限控制器以及PendingFuture实例result来新建一个Accept任务，
    // AcceptTask继承了Iocp.ResultHandler结果处理器接口，这个任务对象做了什么在下面会分析
    AcceptTask task = new AcceptTask(ch, acc, result);
    // 将AcceptTask这个ResultHandler实例也设置到result这个PendingFuture的实例中
    // 在IO操作完成的时候就是根据result.getContext()取出的AcceptTask这个结果处理器来处理结果的
    result.setContext(task);

	// CAS操作，如果accepting之前是false（表示没有发出过accept或者发出的accept已经结束），则表示现在可以发出accept命令，并将accepting设置为true
    if (!accepting.compareAndSet(false, true))
        throw new AcceptPendingException();

    // 发出accept操作
    // 两种方式：
    // 1. 直接由当前执行accept()方法线程发出异步io操作命令；
    // 2. 将异步io操作命令丢到线程池中去，由线程池处理这个任务（存在这样一种情况：当当前执行accept()方法的线程本身也是池中线程，则也会有当前线程执行执行io命令）。
    if (Iocp.supportsThreadAgnosticIo()) {	// 当操作系统支持AgnosticIo时（windows操作系统的主版本>=6的时候即支持-即win7/win2008或者更新的系统）
        task.run();
    } else {
        Invoker.invokeOnThreadInThreadPool(this, task);
    }
    return result;
}
```

从***implAccept()***的实现我们只看到了PendingFuture的创建，但是overlapped与PendingFuture的映射是什么时候建立的好像并没有看到。其实这个映射关系是在**AcceptTask**中建立的，**AcceptTask**是一个内部类，我们看一下它的***run()***方法，看一下这个任务到底做了什么：

```java
@Override
public void run() {
    long overlapped = 0L;	// 初始化一个overlapped值，overlapped其实是一个指向一块OVERLAPPED结构大小的本地内存地址

    try {
        begin();	// 获取服务端异步通道的关闭读锁，防止被关闭
        try {
            channel.begin(); // 获取上面创建的客户端异步通道的关闭读锁，防止被关闭

            // 注意这里锁住了result实例（PendingFuture实例），
            // 跟上面的EventHandlerTask中处理IO完成事件时判断result.isDone()时锁result是呼应的
            synchronized (result) {
                // 将result加入到ioCache中，做了两件事：
                // 1. 判断ioCache中OVERLAPPED结构的缓存是否有空闲的，没有则分配一个新的内存大小；
                // 2. 将这个新内存的地址与result放入到一个Map中，并将这个新内存块的地址作为结果返回
                overlapped = ioCache.add(result);
				
                // 本地方法调用
                // 利用监听套接字（服务端异步通道封装的套接字）、接收套接字（客户端）、
                // overlapped和dataBuffer（用于指向接收的连接的本地和远程地址）来发出一个accept命令
                // 其实这里overlapped指向的地址不会收到任何数据，因为这里的accept只用于接收连接，并不会去接收连接的第一个发送块
                // 而这里之所以要传overlapped的值是windows提供的接口规定不能不能传NULL
                int n = accept0(handle, channel.handle(), overlapped, dataBuffer);
                if (n == IOStatus.UNAVAILABLE) {
                    // accept0方法返回这个值说明当前没有连接可用，需要等待连接到来
                    // 但是因为现在是异步操作，所以底层会返回一个UNAVAILABLE的状态值标识
                    // 无连接直接结束任务，等有连接的时候有异步处理即可
                    return;
                }

                // 进入到这里，说明一发出accept命令就有连接到来或者之前就有连接在连接队列等待处理
                // 这个时候就需要处理连接完成事件，那在这里可能就会跟上面Iocp中EventhandlerTask中存在竞争
                // （因为有连接完成事件就会有Completion Packet发到FIFO队列，EventHandlerTask阻塞等待状态会被打破）
                // 所以在这里处理结果的时候要对result进行加锁
                finishAccept();

                // allow another accept before the result is set
                enableAccept();
                result.setResult(channel);
            }
        } finally {
            // end usage on child socket
            channel.end();
        }
    } catch (Throwable x) {
        // failed to initiate accept so release resources
        if (overlapped != 0L)
            ioCache.remove(overlapped);
        closeChildChannel();
        if (x instanceof ClosedChannelException)
            x = new AsynchronousCloseException();
        if (!(x instanceof IOException) && !(x instanceof SecurityException))
            x = new IOException(x);
        enableAccept();
        result.setFailure(x);
    } finally {
        // end of usage of listener socket
        end();
    }

    // accept completed immediately but may not have executed on
    // initiating thread in which case the operation may have been
    // cancelled.
    if (result.isCancelled()) {
        closeChildChannel();
    }

    // invoke completion handler
    Invoker.invokeIndirectly(result);
}
```

