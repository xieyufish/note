Java中的文件IO之FileChannel

在上一篇文章中，我们对第一代流式文件读写的方式进行了介绍。本文将介绍Java中提供的第二代文件读写实现方式-**FileChannel**。

**FileChannel**除了将读写功能整合到一个类中之外，对读写操作进行了优化用来减少数据在内存之间的拷贝。同时还提供了在第一代流式文件中所不支持的操作文件的方式：内存映射方式（mmap）以及内核之间直接拷贝的方式（sendfile）。下面我们一起来认识一下这个**FileChannel**及其实现吧。

## 一、定义及特性

跟网络NIO实现一样，Java中的文件NIO也是暴露的抽象实现**FileChannel**给用户使用，具体的实现也是跟底层文件系统的实现相关的。我们先看一下这个类的定义吧：

```java
public abstract class FileChannel
    extends AbstractInterruptibleChannel
    implements SeekableByteChannel, GatheringByteChannel, ScatteringByteChannel
```

继承自**AbstractInterruptibleChannel**抽象类，意味着在文件操作的过程中，如果某个操作线程被中断会导致这个文件通道被关闭，从而所有其他的操作文件线程（IO操作未完成的线程）会收到异步关闭的异常错误。

实现了**GatheringByteChannel**和**ScatteringByteChannel**接口，意味着跟网络NIO一样，可以同时将多个缓存的内容按序写入文件也可以将文件内存读到多个缓存中，用图表示如下：

![image-20200715155901832](.\images\image-20200715155901832.png)

而**SeekableByteChannel**则给FileChannel赋予了随机读写文件的能力。

### 1. 文件控制属性特性支持

在之前介绍流式文件操作的时候，我们在打开文件的操作中是无法提供文件控制属性来控制文件的操作行为的。在**FileChannel**中对这一点做了很大的改进，让我们在打开文件的时候可以提供一个选项集用于控制文件的操作属性，其打开操作的定义如下：

```java
/**
 * 打开一个文件，并封装到通道实例中
 */
public static FileChannel open(Path path,	// 文件路径对象
                               Set<? extends OpenOption> options,	// 控制选项
                               FileAttribute<?>... attrs)	// 属性
    throws IOException
{
    /**
     * SPI机制，支持不同操作平台提供不同的文件系统实现，一切的源头由FileSystems这个类开始（因为Path由这个类产生）
     * 会通过sun.nio.fs.DefaultFileSystemProvider.create()的不同平台实现来提供不同的文件系统
     * 但是他们最终创建的FileChannel实例都是sun.nio.ch.FileChannelImpl这个实现类的实例
     */
    FileSystemProvider provider = path.getFileSystem().provider();
    /**
     * 在Windows平台下（其他平台下的流程都是配套的）：
     * 通过WindowsFileSystemProvider（UnixFileSystemProvider）来创建一个文件通道实例
     * 最终会调用WindowsChannelFactory（UnixFileChannel）.newFileChannel来创建
     * newFileChannel方法中会对options标准选项转换为Windows平台（Unix|Linux平台）的文件控制选项值
     * 最终也是通过CreateFileW（open）这个windows（Linux）库函数来完成打开文件的操作
     * （更FileInputStream和FileOutputStream里面一样，只不过将文件控制选项外放到了Java层面）
     */
    return provider.newFileChannel(path, options, attrs);
}
/**
 * 重载实现
 */
public static FileChannel open(Path path, OpenOption... options)
    throws IOException
{
    Set<OpenOption> set = new HashSet<OpenOption>(options.length);
    Collections.addAll(set, options);
    return open(path, set, NO_ATTRIBUTES);
}
```

对于**OpenOption**接口，java中提供了一个标准的枚举实现类**StandardOpenOption**，其定义的枚举值如下表：

| 枚举值            | 描述                                                         |
| ----------------- | ------------------------------------------------------------ |
| READ              | 以读方式打开文件                                             |
| WRITE             | 以写方式打开文件                                             |
| APPEND            | 在指定了WRITE的情况下，以在文件末尾追加内存的方式打开文件    |
| TRUNCATE_EXISTING | 在指定了WRITE的情况下，会清空文件内容；在READ情况下，无作用  |
| CREATE            | 如果待打开文件不存在则创建                                   |
| CREATE_NEW        | 如果待打开文件不存在则创建，否则打开失败                     |
| DELETE_ON_CLOSE   | 在关闭打开文件的时候将文件删除，主要用于临时文件             |
| SPARSE            | 创建一个稀疏文件                                             |
| SYNC              | 将对文件内容以及文件元数据的每次更新操作都同步到存储设备上(也会写到文件系统缓存中) |
| DSYNC             | 将对文件内容的每次更新都同步到存储设备上（也会写到文件系统缓存中） |

利用**FileChannel**打开一个文件的编程模式：

```Java
Path path = FileSystems.getDefault().getPath("dir","file_name");
Set<OpenOption> openOptions = new HashSet<>();
openOptions.add(StandardOpenOption.READ);
openOptions.add(...);
FileChannel fileChannel = FileChannel.open(path, openOptions);
fileChannel.read(...);
```

### 2. 读写方法的改进

在**FileChannel**中对读写方法的改进主要体现在字节缓冲的应用上，而字节缓冲跟普通的字节数组在效率上差别体现可以查看[这篇文章](.\Java中的Buffer.md)中的介绍。我们看一下各读写方法的定义吧：

```java
/**
 * 将文件内容读到字节缓冲dst中
 */
public abstract int read(ByteBuffer dst) throws IOException;
/**
 * Scattering方式读，将内容读到offset~len-1数组下标区间中的ByteBuffer中
 */
public abstract long read(ByteBuffer[] dsts, int offset, int length)
    throws IOException;
/**
 * Scattering方式读
 */
public final long read(ByteBuffer[] dsts) throws IOException {
    return read(dsts, 0, dsts.length);
}
/**
 * 随机访问
 */
public abstract int read(ByteBuffer dst, long position) throws IOException;
/**
 * 将字节缓冲中的内容写入到文件中
 */
public abstract int write(ByteBuffer src) throws IOException;
/**
 * Gathering方式写，将字节缓冲数组中指定下标对应的字节缓冲写入到文件中
 */
public abstract long write(ByteBuffer[] srcs, int offset, int length)
        throws IOException;
/**
 * Gathering方式写
 */
public final long write(ByteBuffer[] srcs) throws IOException {
    return write(srcs, 0, srcs.length);
}

public abstract int write(ByteBuffer src, long position) throws IOException;
```

上面ByteBuffer的创建建议都尽量使用`ByteBuffer.allocateDirect()`方法。

:thinking:**个人想法：**其实上面的读写方法，对于原本是DirectByteBuffer或者是只用DirectByteBuffer的话效率肯定是有提升的，但是其实我们在整个应用过程中，我们在将内容写入到DirectByteBuffer或者从DirectByteBuffer中再取数据处理使用的时候，最终还是会涉及本地直接内存到JVM管理内存的一个拷贝过程，从整个流程来看耗时应该是差不多的。但是其提供一次读满缓存或者是写满缓存的功能还是会有效减少用户态到内核态的切换次数从而提升性能，所以在比较性能的时候我可以着重跟BufferedInputStream(FileInputStream)这种方式来比较看看性能差距。

**FileChannel**中读写方法的实现（**FileChannelImpl**）都是借助的**sun.nio.ch.IOUtil**这个工具类来实现的（网络NIO也一样是通过这个类来完成读写功能的），我们可以看一个实现，看一下是怎么调用的IOUtil这个工具类的：

```java
/**
 * Scattering方式读
 */
public long read(ByteBuffer[] dsts, int offset, int length)
    throws IOException
{
    if ((offset < 0) || (length < 0) || (offset > dsts.length - length))
        throw new IndexOutOfBoundsException();
    ensureOpen();	// 确保通道打开，创建的时候默认就是打开状态直到调用close
    if (!readable)
        // 不可读
        throw new NonReadableChannelException();
    synchronized (positionLock) {	// 获取文件位置锁，意味着与其他操作会互斥
        long n = 0;
        int ti = -1;
        try {
            begin();	// 标记IO操作开始，如果此操作被中断会导致通道关闭，其他未完成IO操作的线程会抛出异步关闭异常
            ti = threads.add();
            if (!isOpen())	// 再次检测打开状态
                return 0;
            do {
                // 通过IOUtil完成读文件功能
                n = IOUtil.read(fd, dsts, offset, length, nd);
            } while ((n == IOStatus.INTERRUPTED) && isOpen());
            return IOStatus.normalize(n);	// 转换结果n
        } finally {
            threads.remove(ti);
            end(n > 0);	// 标记IO操作完成
            assert IOStatus.check(n);
        }
    }
}
```

其他的读写方式基本也就遵循着上面的模式，至于IOUtil是怎么实现的读写，请参考[这篇文章](.\Java中NIO核心工具类-IOUtil.md)。

**FileChannel**中除了改进了打开以及读写功能外，还新增了很多的特性，比如mmap、sendfile等，下面也将会一一介绍。

### 3. map方法

***FileChannel.map()***方法是对底层**mmap**功能的一个封装，mmap是系统提供给我们的一个特性，让我们可以将一块虚拟内存映射到一个文件（或者是一个文件的一部分），mmap会返回映射虚拟内存块的首地址指针，通过操作这个指针可以将文件内容从存储设备上读取到物理内存，从而也完成了虚拟内存到物理内存的映射（增加进程页表映射记录）。用一个图来表示mmap的功能如下：

![image-20200717144611761](.\images\image-20200717144611761.png)

:warning:**不确定点：**网上有很多博文说mmap是通过映射到内核的缓存块，在读写时将文件内容读到内核块，在操作映射块是其实是操作的内核缓存块的说法，但是我看mmap的手册，并没有提到是将其映射到内核某个缓存块的说话；这一点我也无法确定，所以我这里是划的我自己的一个理解图，所以特此补充说一下。

好了，对mmap原理有了一个简单的介绍，下面我们还是来看一下**FileChannel.map()**方法的实现吧：

```java
/**
 * MapMode：映射模式，控制映射的文件块对其他进程的可见性
 * position：从fd文件的position位置开始映射
 * size：映射的文件区域长度
 */
public MappedByteBuffer map(MapMode mode, long position, long size)
    throws IOException
{
    ensureOpen();	// 确保FileChannel的状态是open
    if (mode == null)
        throw new NullPointerException("Mode is null");
    if (position < 0L)
        throw new IllegalArgumentException("Negative position");
    if (size < 0L)
        throw new IllegalArgumentException("Negative size");
    if (position + size < 0)	// 太大溢出
        throw new IllegalArgumentException("Position + size overflow");
    if (size > Integer.MAX_VALUE) // 最大限制2GB文件
        throw new IllegalArgumentException("Size exceeds Integer.MAX_VALUE");

    // 模式与值的转换
    int imode = -1;
    if (mode == MapMode.READ_ONLY)
        imode = MAP_RO; // 0
    else if (mode == MapMode.READ_WRITE)
        imode = MAP_RW; // 1
    else if (mode == MapMode.PRIVATE)
        imode = MAP_PV; // 2
    assert (imode >= 0);
    if ((mode != MapMode.READ_ONLY) && !writable)
        // 打开的文件通道以非写模式打开的，则mode只能指定为RO
        throw new NonWritableChannelException();
    if (!readable)
        // 不可读方式打开的通道不允许映射
        throw new NonReadableChannelException();

    long addr = -1;
    int ti = -1;
    try {
        begin();	// 标记IO操作开始
        ti = threads.add();
        if (!isOpen())	// 再次确认是否open状态
            return null;

        long filesize;
        do {
            filesize = nd.size(fd); // 获取文件的长度，Linux中通过fstat获取文件状态就包含有文件大小信息
        } while ((filesize == IOStatus.INTERRUPTED) && isOpen());
        if (!isOpen())
            return null;

        // 扩充文件大小
        if (filesize < position + size) { // Extend file size
            if (!writable) {
                throw new IOException("Channel not open for writing " +
                                      "- cannot extend file to required size");
            }
            int rv;
            do {
                // truncate本地方法通过ftruncate系统调用来完成
                // 1. position+size>文件原长度，用0填充扩大文件
                // 2. position+siez<文件原长度，进行截断
                rv = nd.truncate(fd, position + size);
            } while ((rv == IOStatus.INTERRUPTED) && isOpen());
            if (!isOpen())
                return null;
        }
        if (size == 0) {
            // 如果设置待映射长度为0，则设置一个非法的地址，并封装一个MappedByteBuffer返回
            // 这个判断为什么不放在开头呢，还会浪费一次系统调用
            addr = 0;
            FileDescriptor dummy = new FileDescriptor();
            if ((!writable) || (imode == MAP_RO))
                // 只读模式的MappedByteBuffer（DirectByteBufferR的实例）
                return Util.newMappedByteBufferR(0, 0, dummy, null);
            else
                return Util.newMappedByteBuffer(0, 0, dummy, null);
        }
		
        // allocationGranularity：通过本地方法获取的内存页大小值-一般是4kb
        // 重新计算映射位置，使position必须是内存页大小的整数倍位置
        int pagePosition = (int)(position % allocationGranularity); // 对内存页取余
        long mapPosition = position - pagePosition;	// 减去余数，往小方向与内存页对齐
        long mapSize = size + pagePosition;	// 扩大实际的映射范围
        try {
            // 通过本地方法完成映射，返回映射成功之后映射内存块的首地址
            addr = map0(imode, mapPosition, mapSize);
        } catch (OutOfMemoryError x) {
            // 内存溢出，进程可用的堆空间不足
            System.gc(); // 触发一次垃圾回收
            try {
                Thread.sleep(100);
            } catch (InterruptedException y) {
                Thread.currentThread().interrupt();
            }
            try {
                // 再尝试一次映射操作
                addr = map0(imode, mapPosition, mapSize);
            } catch (OutOfMemoryError y) {
                // 还是内存不足，则抛出异常
                throw new IOException("Map failed", y);
            }
        }

        // 在Windows平台或者其他可能的平台，对于某些映射区的操作需要一个打开的文件描述符
        // 所以这里我们复制一个文件描述符，指向同一个文件描述符结构
        FileDescriptor mfd;
        try {
            mfd = nd.duplicateForMapping(fd);
        } catch (IOException ioe) {
            // 出现异常，解除映射
            unmap0(addr, mapSize);
            throw ioe;
        }

        assert (IOStatus.checkAll(addr));
        assert (addr % allocationGranularity == 0); // 首地址必须与内存页对齐
        int isize = (int)size;
        // Cleaner中的thunk，用于进行内存释放回收（在回收回调的时候也是通过unmap0()本地方法来解除的映射关系）
        Unmapper um = new Unmapper(addr, mapSize, isize, mfd);
        if ((!writable) || (imode == MAP_RO)) {
            // 只读模式，封装成一个MappedByteBufferR实例（DirectByteBufferR类型）
            return Util.newMappedByteBufferR(isize, // 映射区的大小
                                             addr + pagePosition, // 地址加上对内存页的余数，这样可以防止多次映射时对重叠区的重复读
                                             mfd,	// 传入复制的文件描述符实例
                                             um);	// 传入回收内存的回调
        } else {
            // 读写模式的MappedByteBuffer实例（DirectByteBuffer类型）
            return Util.newMappedByteBuffer(isize,
                                            addr + pagePosition,
                                            mfd,
                                            um);
        }
    } finally {
        threads.remove(ti);
        end(IOStatus.checkAll(addr));	// IO操作完成
    }
}
```

看一下***map0()***本地方法的实现：

```java
//********** Linux平台实现  ********//
JNIEXPORT jlong JNICALL
Java_sun_nio_ch_FileChannelImpl_map0(JNIEnv *env, jobject this,
                                     jint prot, jlong off, jlong len)
{
    void *mapAddress = 0;
    jobject fdo = (*env)->GetObjectField(env, this, chan_fd); // 获取当前FileChannel对象封装的FileDescriptor属性值
    jint fd = fdval(env, fdo); // 获取最终的文件描述符
    int protections = 0;
    int flags = 0;

    if (prot == sun_nio_ch_FileChannelImpl_MAP_RO) { // 0 只读
        protections = PROT_READ;	// 设置映射内存区域的读写权限
        flags = MAP_SHARED;	// 对映射区的更新对其他进程的可见性控制，其他进程可见
    } else if (prot == sun_nio_ch_FileChannelImpl_MAP_RW) { // 1  读写
        protections = PROT_WRITE | PROT_READ;
        flags = MAP_SHARED;
    } else if (prot == sun_nio_ch_FileChannelImpl_MAP_PV) { // 2 私有，写时复制
        protections =  PROT_WRITE | PROT_READ;
        flags = MAP_PRIVATE;	// 修改时会复制一个新的内存区，对其他进程不可见，在刷盘之后，其他进程对这个文件的映射区会失效
    }

    mapAddress = mmap64(
        0,                    /* 指定映射内存块的首地址，为0，有操作系统决定内存块地址 */
        len,                  /* 映射内存区大小 */
        protections,          /* 权限 */
        flags,                /* 修改是否可见 */
        fd,                   /* 要映射的文件描述符 */
        off);                 /* 要映射的文件区域的offset位置 */

    if (mapAddress == MAP_FAILED) {
        // 映射失败，根据错误码决定抛内存溢出异常还是IO异常
        if (errno == ENOMEM) {
            JNU_ThrowOutOfMemoryError(env, "Map failed");
            return IOS_THROWN;
        }
        // 抛IO异常
        return handle(env, -1, "Map failed");
    }
	// 返回映射区的地址
    return ((jlong) (unsigned long) mapAddress);
}
```

解除映射回收内存是通过本地方法***unmap0()***实现，其底层实现是通过***munmap()***系统调用来完成映射关系解除的，实现如下：

```c
JNIEXPORT jint JNICALL
Java_sun_nio_ch_FileChannelImpl_unmap0(JNIEnv *env, jobject this,
                                       jlong address, jlong len)
{
    void *a = (void *)jlong_to_ptr(address);
    // munmap系统调用，解除失败抛出IO异常
    return handle(env,
                  munmap(a, (size_t)len),
                  "Unmap failed");
}
```

通过**FileChannel.map()**映射返回一个MappedByteBuffer实例之后，如果我们对这个映射区做了修改，是不会立即落盘的，也是由操作系统确定落盘时机的；所以如果为了在unmap的时候我们要确保修改落盘，我们可以调用MappedByteBuffer的force方法来落盘，也可以通过FileChannel的force方法来落盘。一般流程如下：

```java
FileChannel fileChannel = FileChannel.open(path, options);
MappedByteBuffer mbb = filcChannel.map(MapMode.READ_WRITE, 0, 1024);
mbb.put(...);
mbb.putchar(...);
mbb.force(); // 或者也可以通过fileChannel.force()。
```

关于MappedByteBuffer的可以参考[这篇文章](.\Java中的Buffer.md)。

:warning:如果我们只是修改了文件的某一个部分，那么MappedByteBuffer.force()应该比FileChannel.force()的效率是要更高的，因为其只是对其映射的那块内存刷到磁盘，而FileChannel.force()是对整个文件落盘（其实现是通过fdatasync或者是fsync来实现的），所以是对磁盘的写肯定是要大；因此在只是修改了某个映射区的情况下，使用MappedByteBuffer.force()是更加高效的。

### 4. transferTo方法

此方法实现的功能是将本文件指定位置开始指定长度的文件内容拷贝到另一个**WritableByteChannel**（可以是文件，也可以是网络），其实现根据目标位置的不同借助不同的方式，sendfile方式或者是mmap方法；mmap原理上面有讲，这里就在简单用图示一下sendfile的底层原理：

![image-20200717154706303](.\images\image-20200717154706303.png)

***sendfile***实现是在操作系统的文件缓存中将一个文件描述符所代表内容拷贝到另一个文件描述符所指向的文件缓存块，并不会将数据拷贝到用户空间。

:warning:sendfile对传输的文件内容大小是有限制的，最大不能超过0x7ffff000（2147479552）字节的大小。

好了，我们看一下java中***FileChannel.transferTo()***方法的实现原理吧。

```java
/**
 * position：从源文件的指定文件开始传送
 * count：控制传送的大小
 * target：接收目标
 */
public long transferTo(long position, long count,
                       WritableByteChannel target)
    throws IOException
{
    ensureOpen();	// 确保当前通道open
    if (!target.isOpen())	// 确保目标通道open
        throw new ClosedChannelException();
    if (!readable)	// 当前通道可读
        throw new NonReadableChannelException();
    if (target instanceof FileChannelImpl &&
        !((FileChannelImpl)target).writable)	
        // 如果目标是文件通道且不可写
        throw new NonWritableChannelException();
    if ((position < 0) || (count < 0))
        throw new IllegalArgumentException();
    long sz = size();	// 获取源文件的大小
    if (position > sz) // 如果position超过了源文件大小，则没必要传送
        return 0;
    int icount = (int)Math.min(count, Integer.MAX_VALUE);	// 控制最大传送量（2147483647）
    if ((sz - position) < icount)	
        // 文件可传送量比要求的小，则重新计算
        icount = (int)(sz - position);

    long n;

    // 先尝试直接传送
    if ((n = transferToDirectly(position, icount, target)) >= 0)
        return n;

    // 再尝试通过映射的方式传送
    if ((n = transferToTrustedChannel(position, icount, target)) >= 0)
        return n;

    // 其他传送方式
    return transferToArbitraryChannel(position, icount, target);
}
```

我们可以看到这个其内部实现也是根据情况多次传送，直到传送成功或者是都传送失败再结束的，现在的关键就是其内部调用的三个方法分别是怎么实现的，我们先来看第一个：***transferToDirectly()***方法的实现。

```java
private long transferToDirectly(long position, int icount,
                                WritableByteChannel target)
    throws IOException
{
    // transferSupported用于标记底层系统是否支持sendfile()传送，默认值为true
    if (!transferSupported)
        return IOStatus.UNSUPPORTED; // -4

    FileDescriptor targetFD = null;
    if (target instanceof FileChannelImpl) {
        // 传送目标对象是文件类型
        // fileSupported用于标记sendfile()是否支持文件之间的传送，因为早期有些系统是只支持socket目标传送的
        // 默认值也是true
        if (!fileSupported)
            return IOStatus.UNSUPPORTED_CASE; // -6
        targetFD = ((FileChannelImpl)target).fd;	// 获取目标文件描述符对象
    } else if (target instanceof SelChImpl) {
        // 传送目标是SelChImpl类型，在java中表示的是NIO网络及Pipe管道类型
        // pipeSupported用于标记sendfile()是否支持目标文件描述是管道类型，默认值true
        if ((target instanceof SinkChannelImpl) && !pipeSupported)
            return IOStatus.UNSUPPORTED_CASE;
        targetFD = ((SelChImpl)target).getFD();	// 获取目标文件描述符对象
    }
    if (targetFD == null)	
        // 目标文件描述符为空
        return IOStatus.UNSUPPORTED;  // -4
    int thisFDVal = IOUtil.fdVal(fd);	// 获取源文件描述符值
    int targetFDVal = IOUtil.fdVal(targetFD);	// 获取目标文件描述符值
    if (thisFDVal == targetFDVal) 
        // 代表同一个对象
        return IOStatus.UNSUPPORTED;

    long n = -1;
    int ti = -1;
    try {
        begin();	// 标记IO操作开始
        ti = threads.add();
        if (!isOpen())
            return -1;
        do {
            // 本地方法实现传送
            n = transferTo0(thisFDVal, position, icount, targetFDVal);
        } while ((n == IOStatus.INTERRUPTED) && isOpen());	// 被中断的话会重试

        if (n == IOStatus.UNSUPPORTED_CASE) {
            // 底层返回目标类型不支持的情况
            if (target instanceof SinkChannelImpl)
                pipeSupported = false;	// 调整值
            if (target instanceof FileChannelImpl)
                fileSupported = false;
            return IOStatus.UNSUPPORTED_CASE;
        }
        if (n == IOStatus.UNSUPPORTED) {
            // 底层不支持sendfile()
            transferSupported = false;
            return IOStatus.UNSUPPORTED;
        }
        return IOStatus.normalize(n);	// 返回结果值-传送成功的数据量大小
    } finally {
        threads.remove(ti);
        end (n > -1);
    }
}

private native long transferTo0(int src, long position, long count, int dst);
```

实现核心功能的代码在于本地方法***transferTo0()***本地方法的调用，我们看一下它的实现：

```c
JNIEXPORT jlong JNICALL
Java_sun_nio_ch_FileChannelImpl_transferTo0(JNIEnv *env, jobject this,
                                            jint srcFD,
                                            jlong position, jlong count,
                                            jint dstFD)
{
#if defined(__linux__)
    // 如果是linux系统，这里之所以要判断系统是因为jdk的源码实现其实是只分了两个平台solaris和windows的代码
    // solaris里面又会再分linux、unix等
    off64_t offset = (off64_t)position;
    // sendfile64()系统调用完成传送
    jlong n = sendfile64(dstFD, srcFD, &offset, (size_t)count);
    if (n < 0) {
        if (errno == EAGAIN)
            // 针对Nonblocking IO 写进行，再次是不会进入到这一条件分支的，因为FileChannel不是NonBlocking模式
            return IOS_UNAVAILABLE; // 对应到IOStatus.UNAVAILABLE
        if ((errno == EINVAL) && ((ssize_t)count >= 0))
            // 源描述符不正确或者是被锁了，或者是一个mmap操作导致的不可用；或者是目标文件描述符是用O_APPEND打开的
            // 返回不支持目标文件
            return IOS_UNSUPPORTED_CASE;
        if (errno == EINTR) {
            // 中断信号
            return IOS_INTERRUPTED;
        }
        // 其他情况，抛出IO异常
        JNU_ThrowIOExceptionWithLastError(env, "Transfer failed");
        return IOS_THROWN;
    }
    return n;
#elif defined (__solaris__)
    // solaris系统
    sendfilevec64_t sfv;
    size_t numBytes = 0;
    jlong result;

    sfv.sfv_fd = srcFD;
    sfv.sfv_flag = 0;
    sfv.sfv_off = (off64_t)position;
    sfv.sfv_len = count;

    result = sendfilev64(dstFD, &sfv, 1, &numBytes);

    /* Solaris sendfilev() will return -1 even if some bytes have been
     * transferred, so we check numBytes first.
     */
    if (numBytes > 0)
        return numBytes;
    if (result < 0) {
        if (errno == EAGAIN)
            return IOS_UNAVAILABLE;
        if (errno == EOPNOTSUPP)
            return IOS_UNSUPPORTED_CASE;
        if ((errno == EINVAL) && ((ssize_t)count >= 0))
            return IOS_UNSUPPORTED_CASE;
        if (errno == EINTR)
            return IOS_INTERRUPTED;
        JNU_ThrowIOExceptionWithLastError(env, "Transfer failed");
        return IOS_THROWN;
    }
    return result;
#elif defined(__APPLE__)
    // 苹果系统
    off_t numBytes;
    int result;

    numBytes = count;

#ifdef __APPLE__
    result = sendfile(srcFD, dstFD, position, &numBytes, NULL, 0);
#endif

    if (numBytes > 0)
        return numBytes;

    if (result == -1) {
        if (errno == EAGAIN)
            return IOS_UNAVAILABLE;
        if (errno == EOPNOTSUPP || errno == ENOTSOCK || errno == ENOTCONN)
            return IOS_UNSUPPORTED_CASE;
        if ((errno == EINVAL) && ((ssize_t)count >= 0))
            return IOS_UNSUPPORTED_CASE;
        if (errno == EINTR)
            return IOS_INTERRUPTED;
        JNU_ThrowIOExceptionWithLastError(env, "Transfer failed");
        return IOS_THROWN;
    }

    return result;
#else
    // 其他系统直接返回不支持sendfile()
    return IOS_UNSUPPORTED_CASE;
#endif
}
```

如果直接transfer失败，也就是说通过sendfile处理不能正确的完成文件内容传送，我们尝试退化到第二种处理方式：通过***transferToTrustedChannel()***方法来完成文件传送。

```java
private long transferToTrustedChannel(long position, long count,
                                      WritableByteChannel target)
    throws IOException
{
    // 如果目标类型不是文件或者NIO网络或者Pipe，直接返回不支持
    boolean isSelChImpl = (target instanceof SelChImpl);
    if (!((target instanceof FileChannelImpl) || isSelChImpl))
        return IOStatus.UNSUPPORTED;

    // Trusted target: Use a mapped buffer
    long remaining = count;
    while (remaining > 0L) {
        // MAPPED_TRANSFER_SIZE：8M
        long size = Math.min(remaining, MAPPED_TRANSFER_SIZE); // 一次最大8M
        try {
            // 已只读的mmap方式进行映射
            MappedByteBuffer dbb = map(MapMode.READ_ONLY, position, size);
            try {
                // 将映射内容写到目标对象
                // 这里存在一个bug，由于mmap映射的内存块在源文件被关闭的情况下依然会有效，
                // 所以这里在通道关闭的情况下并不会终止这里的写操作
                int n = target.write(dbb);	// FileChannel写、NIO写的实现都可以查看我这个系列的文章了解原理哦
                assert n >= 0;	// 是否写成功
                remaining -= n;	// 减去写完的
                if (isSelChImpl) {
                    // 如果是NIO网络，写完一次之后不再写了，等到socket缓冲区可写再进行下一次传送
                    // 猜想：这里应该是跟有些系统默认的socket缓存大小为8M有关
                    break;
                }
                assert n > 0;
                position += n;	// 改变position位置，以进行下一次mmap
            } finally {
                unmap(dbb);	// 此次mmap的内容写完之后，要回收mmap的空间
            }
        } catch (ClosedByInterruptException e) {
            assert !target.isOpen();
            try {
                close();
            } catch (Throwable suppressed) {
                e.addSuppressed(suppressed);
            }
            throw e;
        } catch (IOException ioe) {
            if (remaining == count)
                // 只有在完全没有传送任何字节的情况下才抛出异常
                throw ioe;
            break;
        }
    }
    return count - remaining;	// 返回最终传送成功的大小
}
```

我们可以看到第二种方式是通过mmap和再write的方式来实现的传送，从拷贝次数上来说跟sendfile实现是没有区别的，主要的区别在于内核态与用户态的多次上下文切换会造成第二种方式跟第一种方式在效率上造成的差距。对于源文件小于8M的文件，在效率上差距是不大的（一次映射再加一次write，就多了一次切换）；文件越大差距应该会越明显。

如果第二种传送方式还是失败了，就只能退化到第三种最后的传送方式了：通过***transferToArbitraryChannel()***方法实现传送，其实现如下：

```java
private long transferToArbitraryChannel(long position, int icount,
                                        WritableByteChannel target)
    throws IOException
{
    // TRANSFER_SIZE：8KB，控制每次读的最大大小为8KB
    int c = Math.min(icount, TRANSFER_SIZE);
    // 从Util的直接内存缓冲池中获取一个合适大小的DirectByteBuffer对象
    ByteBuffer bb = Util.getTemporaryDirectBuffer(c);
    long tw = 0;                    // 记录已经传送完的字节数量
    long pos = position;
    try {
        Util.erase(bb);	// 擦除DirectByteBuffer的源内容
        while (tw < icount) {
            // 限制DirectByteBuffer的空闲空间容量不超过8KB
            bb.limit(Math.min((int)(icount - tw), TRANSFER_SIZE));
            // read(bb, pos)就是通过调用IOUtil.read(bytebuffer, position)来完成从文件指定位置开始读的
            // IOUtil的介绍请查看我这个系列的文章的解读
            int nr = read(bb, pos);	// 从文件的pos位置开始将内容读到bb中
            if (nr <= 0)	// 读完了
                break;
            bb.flip();	// 翻转以便于将字节缓冲中的内容写出去
            // 将字节缓冲区的内容再写入到目标
            int nw = target.write(bb);
            tw += nw;
            if (nw != nr)
                // 为什么直接break，而不是bb.compact()继续下次写呢
                // （因为nw!=nr可能表示的是写入的时候有些字节没写完啊）？
                break;
            pos += nw;	// 读位置后移
            bb.clear(); // 字节缓冲清空
        }
        return tw;// 返回最终传送的字节数
    } catch (IOException x) {
        if (tw > 0)
            // 已经成功传送了一些数据，则返回传送成功的数量
            return tw;
        throw x;	// 否则抛出异常
    } finally {
        Util.releaseTemporaryDirectBuffer(bb);	// 字节缓冲回归池中
    }
}
```

第三种方式是通过读写的方式来实现的，效率是最低的，也是实现**transferTo**的保底操作。

**FileChannel**中除了可以将当前文件内容拷贝到另一个目标文件之外，还可以从另一个文件将内容拷贝到本文件中，那就是***transferFrom()***方法。

### 5. transferFrom方法

```java
public long transferFrom(ReadableByteChannel src,
                         long position, long count)
    throws IOException
{
    ensureOpen();
    if (!src.isOpen())
        throw new ClosedChannelException();
    if (!writable)
        throw new NonWritableChannelException();
    if ((position < 0) || (count < 0))
        throw new IllegalArgumentException();
    if (position > size())
        return 0;
    if (src instanceof FileChannelImpl)
        return transferFromFileChannel((FileChannelImpl)src,
                                       position, count);

    return transferFromArbitraryChannel(src, position, count);
}
```

transferFrom方法跟transferTo方法相比要简单些，内部只有两种方式来完成从其他**ReadableByteChannel**拷贝内容的方式，分别是：***transferFromFileChannel()***和***transferFromArbitraryChannel()***方法，从方法名我们就可以看出这两种方式与transferTo中的第二、三种方式是对应的，我们就一起看一下它们的实现吧。

```java
/**
 * 从一个文件中拷贝内容到本文件中
 * position：指定本文件位置
 * count：拷贝大小
 * 文件到文件的方式为什么不用sendfile呢？
 */
private long transferFromFileChannel(FileChannelImpl src,
                                     long position, long count)
    throws IOException
{
    if (!src.readable) // 源文件不可读
        throw new NonReadableChannelException();
    synchronized (src.positionLock) {
        long pos = src.position();
        long max = Math.min(count, src.size() - pos);

        long remaining = max;
        long p = pos;
        while (remaining > 0L) {
            long size = Math.min(remaining, MAPPED_TRANSFER_SIZE);
            // mmap方式
            MappedByteBuffer bb = src.map(MapMode.READ_ONLY, p, size);
            try {
                // 再write
                long n = write(bb, position);
                assert n > 0;
                p += n;
                position += n;
                remaining -= n;
            } catch (IOException ioe) {
                if (remaining == max)
                    throw ioe;
                break;
            } finally {
                unmap(bb);
            }
        }
        long nwritten = max - remaining;
        src.position(pos + nwritten);
        return nwritten;
    }
}
/**
 * 源头为NIO网络、Pipe管道
 * read+write方式
 */
private long transferFromArbitraryChannel(ReadableByteChannel src,
                                          long position, long count)
    throws IOException
{
    int c = (int)Math.min(count, TRANSFER_SIZE);
    ByteBuffer bb = Util.getTemporaryDirectBuffer(c);
    long tw = 0;                    // Total bytes written
    long pos = position;
    try {
        Util.erase(bb);
        while (tw < count) {
            bb.limit((int)Math.min((count - tw), (long)TRANSFER_SIZE));
            int nr = src.read(bb);
            if (nr <= 0)
                break;
            bb.flip();
            int nw = write(bb, pos);
            tw += nw;
            if (nw != nr)
                break;
            pos += nw;
            bb.clear();
        }
        return tw;
    } catch (IOException x) {
        if (tw > 0)
            return tw;
        throw x;
    } finally {
        Util.releaseTemporaryDirectBuffer(bb);
    }
}
```

理解了**transferTo**之后，transferFrom的实现也很容易看懂了，不赘述。

### 6. force方法

当我们修改了文件内容的时候，再write时，其首先是write到文件系统的缓存中的（也要看open时指定了什么样的OpenOption值了），至于什么什么落盘到存储设备上，是有文件系统中的缓存处理器来决定的。那如果我们要确保文件内容落盘怎么办呢？这就是force方法的作用，用于将修改后的文件落盘。我们看一下这个方法的实现：

```java
/**
 * metaData：表示是否需要将文件的元数据也要落盘
 */
public void force(boolean metaData) throws IOException {
    ensureOpen();
    int rv = -1;
    int ti = -1;
    try {
        begin();
        ti = threads.add();
        if (!isOpen())
            return;
        do {
            rv = nd.force(fd, metaData);	// 本地方法调用实现落盘
        } while ((rv == IOStatus.INTERRUPTED) && isOpen());
    } finally {
        threads.remove(ti);
        end(rv > -1);
        assert IOStatus.check(rv);
    }
}
```

看一下本地方法**force0()**的实现吧。

```c
JNIEXPORT jint JNICALL
Java_sun_nio_ch_FileDispatcherImpl_force0(JNIEnv *env, jobject this,
                                          jobject fdo, jboolean md)
{
    jint fd = fdval(env, fdo);	// 获取文件描述符值
    int result = 0;

    if (md == JNI_FALSE) {
        result = fdatasync(fd);	// 只将文件内容刷到磁盘，同步操作
    } else {
        result = fsync(fd);	// 文件内容和文件的元数据信息都刷到磁盘，同步操作
    }
    return handle(env, result, "Force failed");
}
```

### 7. lock方法

实现对文件中的某个部分加锁。

```java
/**
 * position：文件开始处
 * size：文件区域大小
 * shared：控制是读锁还是写锁，true：读锁，false：写锁
 */
public FileLock lock(long position, long size, boolean shared)
    throws IOException
{
    ensureOpen();
    if (shared && !readable)	// 读锁却不可读，抛出异常
        throw new NonReadableChannelException();
    if (!shared && !writable)	// 写锁却不可写，抛出异常
        throw new NonWritableChannelException();
    FileLockImpl fli = new FileLockImpl(this, position, size, shared);	// 实例化一个Java文件锁对象
    /**
     * 获取文件锁表，有两种类型
     * 1. SimpleFileLockTable：简单文件锁表，每个FileChannel维护的一个文件锁表，维护了这个FileChannel的锁信息
     * 2. SharedFileLockTable：共享文件锁表，整个Java进行维护的一个锁表，记录了每个文件的锁信息
     * 
     */
    FileLockTable flt = fileLockTable();
    flt.add(fli);	// 将创建的锁实例加入锁表，如果新建的锁实例有跟已经存在的锁有区域重叠的情况会抛出异常
    boolean completed = false;
    int ti = -1;
    try {
        begin();
        ti = threads.add();
        if (!isOpen())
            return null;
        int n;
        do {
            // 通过系统调用对文件描述符所代表的文件进行控制
            // 在java层面不会维护底层的锁结构，直接由系统去维护
            // java层面只维护FileLock实例，这个实例跟底层的文件锁是没有建立关联关系的
            // 这里锁如果冲突了，会等到其他锁释放
            n = nd.lock(fd, true, position, size, shared);
        } while ((n == FileDispatcher.INTERRUPTED) && isOpen());
        if (isOpen()) {
            if (n == FileDispatcher.RET_EX_LOCK) {
                assert shared;
                FileLockImpl fli2 = new FileLockImpl(this, position, size,
                                                     false);
                flt.replace(fli, fli2);
                fli = fli2;
            }
            completed = true;
        }
    } finally {
        if (!completed)
            flt.remove(fli);
        threads.remove(ti);
        try {
            end(completed);
        } catch (ClosedByInterruptException e) {
            throw new FileLockInterruptionException();
        }
    }
    return fli;
}
```

看一下**lock0()**本地方法的实现吧。

```c
JNIEXPORT jint JNICALL
Java_sun_nio_ch_FileDispatcherImpl_lock0(JNIEnv *env, jobject this, jobject fdo,
                                      jboolean block, jlong pos, jlong size,
                                      jboolean shared)
{
    jint fd = fdval(env, fdo);	// 获取文件描述符值
    jint lockResult = 0;
    int cmd = 0;
    struct flock64 fl;	// 一个flock结构体，用于描述这个锁的信息

    fl.l_whence = SEEK_SET;	// 解析锁起点（l_start）的方式
    if (size == (jlong)java_lang_Long_MAX_VALUE) {
        fl.l_len = (off64_t)0;	// 锁定的字节长度
    } else {
        fl.l_len = (off64_t)size;
    }
    fl.l_start = (off64_t)pos;	// 锁的起点
    if (shared == JNI_TRUE) {
        fl.l_type = F_RDLCK;	// 锁类型，读锁
    } else {
        fl.l_type = F_WRLCK;	// 写锁
    }
    if (block == JNI_TRUE) {
        cmd = F_SETLKW64;	// lock()走这个分支；跟已经存在的文件锁冲突时，等待其他锁释放
    } else {
        cmd = F_SETLK64;	// tryLock()走这个分支；获取锁，如果有冲突返回-1，错误信息通过错误码获取
    }
    // fcntl系统调用，对fd指定的文件执行一个cmd指定的操作
    lockResult = fcntl(fd, cmd, &fl);
    if (lockResult < 0) {
        // 锁失败了
        if ((cmd == F_SETLK64) && (errno == EAGAIN || errno == EACCES))
            // 操作被其他进程持有的锁禁止了
            return sun_nio_ch_FileDispatcherImpl_NO_LOCK;
        if (errno == EINTR)
            // 中断处理
            return sun_nio_ch_FileDispatcherImpl_INTERRUPTED;
        JNU_ThrowIOExceptionWithLastError(env, "Lock failed");
    }
    return 0;	// 返回0，并不是返回锁结构
}
```

**lock()**方法会阻塞等待，一直到其他进程或现场释放指定区域的锁；如果我们不想阻塞，可以使用***tryLock()***方法，这个方法就是如果能获取锁就获取，不能获取就返回null。

### 8. 其他方法

**FileChannel**中还提供了其他的方法，比如定位到文件的某个位置、获取文件的长度、截断文件等各种功能，这些功能都比较简单，或者在介绍上面的方法时也都有提及，就不再赘述了。感兴趣的可以自己研究研究。