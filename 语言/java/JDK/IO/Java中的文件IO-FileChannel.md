# Java中的文件IO之FileChannel

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