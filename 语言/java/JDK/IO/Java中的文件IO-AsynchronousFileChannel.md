# Java中的文件IO之AsynchronousFileChannel

**AsynchronousFileChannel**是Java对异步文件IO的支持，跟异步网络IO一样，都属于异步IO体系。在讲异步网络IO的时候，我就提过异步IO的实现是依赖于提供系统实现机制的；在Windows中是依赖于完成端口机制，借助线程池来完成的异步功能；而Linux中异步网络IO的实现还是借助的epoll机制，而异步文件IO底层并没有提供额外的支持，所以在Linux平台的异步文件IO是Java层面封装的异步实现。

本文将聚焦在Windows平台下的异步文件IO实现，看过之前Windows下异步网络IO实现文章的同学对于异步文件IO的实现看起来应该是很容易理解的，因为他们之间从实现原理上其实是没有区别的，只不过就是将文件描述符换成了文件对象而不再是socket对象了。

所以本文讲解将不会很详细，详细的过程可以参考[Windows下异步网络IO的实现](.\Java中异步IO的实现-网络-Windows.md)这篇文章。

## 一、 AsynchronousFileChannel

**AsynchronousFileChannel**是Java提供的文件异步IO的抽象，通过这一个抽象类程序员就可以完成所有有关文件异步IO的所有操作，对使用者完全屏蔽了底层的具体实现，这种思想在我们平时写SDK的时候可以借鉴一下，提供一个抽象即可完成所有的实现。

跟另外两种文件IO一样，我们先看看这个抽象类给我们提供了哪些操作文件的能力。

### 1. 打开文件

```java
/**
 * 打开指定路径下的文件，并封装到一个AsynchronousFileChannel对象中
 * 只能传入executor型的线程池，说明只能是cached类型的线程池
 */
public static AsynchronousFileChannel open(Path file,
                                           Set<? extends OpenOption> options,
                                           ExecutorService executor,
                                           FileAttribute<?>... attrs)
    throws IOException
{
    FileSystemProvider provider = file.getFileSystem().provider();	// 跟FileChannel一样，先获取文件系统提供器对象
    // 通过提供器创建一个新的异步文件通道对象
    // WindowsFileSystemProvider->WindowsChannelFactory->new WindowsAsynchronousFileChannelImpl
    return provider.newAsynchronousFileChannel(file, options, executor, attrs);
}

/**
 * 重载实现，调用的上面的方法
 */
public static AsynchronousFileChannel open(Path file, OpenOption... options)
    throws IOException
{
    Set<OpenOption> set = new HashSet<OpenOption>(options.length);
    Collections.addAll(set, options);
    return open(file, set, null, NO_ATTRIBUTES);
}
```

看一下打开一个文件的代码吧，在**WindowsChannelFactory**这个类中实现的，代码如下：

```java
/**
 * 创建一个AsynchronousFileChannel对象
 */
static AsynchronousFileChannel newAsynchronousFileChannel(String pathForWindows,
                                                          String pathToCheck,
                                                          Set<? extends OpenOption> options,
                                                          long pSecurityDescriptor,
                                                          ThreadPool pool)
    throws IOException
{
    Flags flags = Flags.toFlags(options);

    // Windows中针对异步IO需要增加一个标记overlapped，否则不能执行异步IO操作
    flags.overlapped = true;

    // 默认以读操作打开
    if (!flags.read && !flags.write) {
        flags.read = true;
    }

    // 异步IO不能以append方式打开
    if (flags.append)
        throw new UnsupportedOperationException("APPEND not allowed");

    FileDescriptor fdObj;
    try {
        // 打开文件（open方法内部会进行标准属性转换，调用本地方法打开指定文件等一系列操作）
        fdObj = open(pathForWindows, pathToCheck, flags, pSecurityDescriptor);
    } catch (WindowsException x) {
        x.rethrowAsIOException(pathForWindows);
        return null;
    }

    try {
        // 创建一个WindowsAsynchronousFileChannelImpl的实例
        // 会将这个打开的文件描述符与Iocp关联起来，可以看如下的实现代码
        return WindowsAsynchronousFileChannelImpl.open(fdObj, flags.read, flags.write, pool);
    } catch (IOException x) {
        long handle = fdAccess.getHandle(fdObj);
        CloseHandle(handle);
        throw x;
    }
}

///// WindowsAsynchronousFileChannelImpl.java/////////////

public static AsynchronousFileChannel open(FileDescriptor fdo,
                                           boolean reading,
                                           boolean writing,
                                           ThreadPool pool)
    throws IOException
{
    Iocp iocp;
    boolean isDefaultIocp;
    // 创建一个Iocp实例
    if (pool == null) {	
        // open的时候没有提供线程池实例，就创建一个默认的Iocp实例（线程池是cached类型）
        iocp = DefaultIocpHolder.defaultIocp;
        isDefaultIocp = true;
    } else {
        // 提供了线程池实例，则利用指定的线程池创建一个Iocp实例
        iocp = new Iocp(null, pool).start();
        isDefaultIocp = false;
    }
    try {
        // new一个实例
        return new
            WindowsAsynchronousFileChannelImpl(fdo, reading, writing, iocp, isDefaultIocp);
    } catch (IOException x) {
        // error binding to port so need to close it (if created for this channel)
        if (!isDefaultIocp)
            iocp.implClose();
        throw x;
    }
}

// 构造方法，创建一个实例
private WindowsAsynchronousFileChannelImpl(FileDescriptor fdObj,
                                           boolean reading,
                                           boolean writing,
                                           Iocp iocp,
                                           boolean isDefaultIocp)
    throws IOException
{
    super(fdObj, reading, writing, iocp.executor());
    this.handle = fdAccess.getHandle(fdObj);
    this.iocp = iocp;
    this.isDefaultIocp = isDefaultIocp;
    this.ioCache = new PendingIoCache();	// 用于存放读或者写的文件内容的缓存
    this.completionKey = iocp.associate(this, handle);	// 将打开文件与iocp关联起来
}
```

### 2. 读写能力

```java
public abstract <A> void read(ByteBuffer dst,
                              long position,
                              A attachment,
                              CompletionHandler<Integer,? super A> handler);

public abstract Future<Integer> read(ByteBuffer dst, long position);

public abstract <A> void write(ByteBuffer src,
                               long position,
                               A attachment,
                               CompletionHandler<Integer,? super A> handler);

public abstract Future<Integer> write(ByteBuffer src, long position);
```

关于异步IO操作的两种操作形式：**CompletionHandler**和**Future**形式在讲网络IO的时候也提过，最终的实现都是通过**implRead()**和**implWrite()**来完成，这里也不再赘述。

### 3. 文件片段锁能力

```java
/**
 * 锁住文件从position开始size长度的片段
 * 注意：shared参数并不是说锁的性质是共享锁还是非共享锁，它是只指创建的锁对象是放在当前的通道实例还是方法一个功能的map中
 */
public abstract <A> void lock(long position,
                              long size,
                              boolean shared,
                              A attachment,
                              CompletionHandler<FileLock,? super A> handler);

/**
 * 锁整个文件
 */
public final <A> void lock(A attachment,
                           CompletionHandler<FileLock,? super A> handler)
{
    lock(0L, Long.MAX_VALUE, false, attachment, handler);
}
/**
 * Future形式锁文件片段
 */
public abstract Future<FileLock> lock(long position, long size, boolean shared);
/**
 * Future形式锁文件
 */
public final Future<FileLock> lock() {
    return lock(0L, Long.MAX_VALUE, false);
}

public abstract FileLock tryLock(long position, long size, boolean shared)
        throws IOException;

public final FileLock tryLock() throws IOException {
    return tryLock(0L, Long.MAX_VALUE, false);
}
```

### 4. 文件截断

```java
public abstract AsynchronousFileChannel truncate(long size) throws IOException;
```

注意：这个函数是有扩充能力的，如果size比当前文件尺寸小则截断；否则扩充。

从方法定义就可以看出这个方法是没有异步能力的。

### 5. 刷盘

```java
public abstract void force(boolean metaData) throws IOException;
```

无异步能力。

### 6. 文件尺寸

```java
public abstract long size() throws IOException;
```

无异步能力。

## 二、具体实现

跟网络异步IO一样，在具体的实现上，Java首先提供了一个抽象实现，具体的实现也都是由平台做的。抽象实现都很简单，就是对具体实现的一个调用。所以对于抽象的实现这里就不过多解释了。具体的实现大家其实也可以直接参考网络异步IO那篇文章，这里以异步读操作为例进行简单的讲解吧，不过多讲述了。

实现代码如下：

```java
///////// 抽象实现：AsynchronousFileChannelImpl.java //////////////////
@Override
public final Future<Integer> read(ByteBuffer dst, long position) {
    return implRead(dst, position, null, null);
}

@Override
public final <A> void read(ByteBuffer dst,
                           long position,
                           A attachment,
                           CompletionHandler<Integer,? super A> handler)
{
    if (handler == null)
        throw new NullPointerException("'handler' is null");
    implRead(dst, position, attachment, handler);
}

abstract <A> Future<Integer> implRead(ByteBuffer dst,
                                      long position,
                                      A attachment,
                                      CompletionHandler<Integer,? super A> handler);

//////// 具体实现：WindowsAsynchronousFileChannelImpl.java /////////////////////
@Override
<A> Future<Integer> implRead(ByteBuffer dst,
                             long position,
                             A attachment,
                             CompletionHandler<Integer,? super A> handler)
{
    if (!reading)
        throw new NonReadableChannelException();
    if (position < 0)
        throw new IllegalArgumentException("Negative position");
    if (dst.isReadOnly())
        throw new IllegalArgumentException("Read-only buffer");

    // check if channel is closed
    if (!isOpen()) {
        Throwable exc = new ClosedChannelException();
        if (handler == null)
            return CompletedFuture.withFailure(exc);
        Invoker.invoke(this, handler, attachment, null, exc);
        return null;
    }

    int pos = dst.position();
    int lim = dst.limit();
    assert (pos <= lim);
    int rem = (pos <= lim ? lim - pos : 0);

    if (rem == 0) {
        // 字节缓冲对象中无可用空间
        if (handler == null)
            return CompletedFuture.withResult(0);
        Invoker.invoke(this, handler, attachment, 0, null);
        return null;
    }

    // 创建一个Future任务，并实例化一个read任务
    PendingFuture<Integer,A> result =
        new PendingFuture<Integer,A>(this, handler, attachment);
    ReadTask<A> readTask = new ReadTask<A>(dst, pos, rem, position, result);
    result.setContext(readTask);

    // 发出 I/O 操作
    if (Iocp.supportsThreadAgnosticIo()) {
        readTask.run();
    } else {
        Invoker.invokeOnThreadInThreadPool(this, readTask);
    }
    return result;
}
```

核心**ReadTask**任务的实现代码如下：

```java
@Override
public void run() {
    int n = -1;
    long overlapped = 0L;	// overlapped的内存地址
    long address;

    // 如果不是DirectBuffer，则分配一个本地缓存
    if (dst instanceof DirectBuffer) {
        buf = dst;
        address = ((DirectBuffer)dst).address() + pos;
    } else {
        buf = Util.getTemporaryDirectBuffer(rem);
        address = ((DirectBuffer)buf).address();
    }

    boolean pending = false;
    try {
        begin();

        // 分配 OVERLAPPED，并与PendingFuture映射起来
        overlapped = ioCache.add(result);

        // 发出 read 操作，本地方法的调用
        n = readFile(handle, address, rem, position, overlapped);
        if (n == IOStatus.UNAVAILABLE) {
            // I/O 正在进行
            pending = true;
            return;
        } else if (n == IOStatus.EOF) { // 读操作马上就完成了
            result.setResult(n);
        } else {
            throw new InternalError("Unexpected result: " + n);
        }

    } catch (Throwable x) {
        // 发出read操作失败了
        result.setFailure(toIOException(x));
    } finally {
        if (!pending) {
            // 释放资源
            if (overlapped != 0L)
                ioCache.remove(overlapped);
            releaseBufferIfSubstituted();
        }
        end();
    }

    // 调用CompletionHandler
    Invoker.invoke(result);
}

/**
 * io操作完成之后的回调函数
 */
@Override
public void completed(int bytesTransferred, boolean canInvokeDirect) {
    updatePosition(bytesTransferred);	// 将读取的内容拷贝到字节缓冲中，并调整position的位置

    // 将本地缓存返回到池中
    releaseBufferIfSubstituted();

    // 设置结果，并调用结果处理器
    result.setResult(bytesTransferred);
    if (canInvokeDirect) {
        Invoker.invokeUnchecked(result);
    } else {
        Invoker.invoke(result);
    }
}

/**
 * io操作失败之后的回调函数
 */
@Override
public void failed(int error, IOException x) {
    if (error == ERROR_HANDLE_EOF) {
        completed(-1, false);
    } else {
        releaseBufferIfSubstituted();

        if (isOpen()) {
            result.setFailure(x);
        } else {
            result.setFailure(new AsynchronousCloseException());
        }
        Invoker.invoke(result);
    }
}
```

看一下本地方法***readFile()***的实现：

```c
JNIEXPORT jint JNICALL
Java_sun_nio_ch_WindowsAsynchronousFileChannelImpl_readFile(JNIEnv* env, jclass this,
    jlong handle, jlong address, jint len, jlong offset, jlong ov)
{
    BOOL res;

    // 从文件哪里开始读的一个overlapped结构，在打开文件是以overlapped打开的时候必须指定这个结构体参数
    OVERLAPPED* lpOverlapped = (OVERLAPPED*)jlong_to_ptr(ov);
    lpOverlapped->Offset = (DWORD)offset;
    lpOverlapped->OffsetHigh = (DWORD)((long)(offset >> 32));
    lpOverlapped->hEvent = NULL;

    // ReadFile系统函数调用
    res = ReadFile((HANDLE) jlong_to_ptr(handle),	// 文件句柄
                   (LPVOID) jlong_to_ptr(address),	// 指向接收内容的内存地址
                   (DWORD)len,	// 要读取的最大长度
                   NULL,	// 实际读取的字节长度，在异步io的时候设置为NULL值
                   lpOverlapped);	// overlapped结构，异步IO的时候必须指定

    if (res == 0) {	
        // 失败，或者是异步IO发出完成
        int error = GetLastError();
        if (error == ERROR_IO_PENDING)
            return IOS_UNAVAILABLE;	// 异步IO真正进行
        if (error == ERROR_HANDLE_EOF)
            return IOS_EOF;	// 已经是读到文件末尾了
        JNU_ThrowIOExceptionWithLastError(env, "ReadFile failed");
        return IOS_THROWN;
    }

    return IOS_UNAVAILABLE;	// 返回异步IO正在进行
}
```

其他的异步方法就不讲了，理解了异步网络IO对其他的方法应该也就能理解了，实现都是一样的。