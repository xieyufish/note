# Java NIO中的核心工具之IOUtil类

**IOUtil**是Java提供的在*sun.nio.ch*包下的一个用于NIO操作的工具类，提供了基于**FileDescriptor**类来完成对底层文件描述符进行读写的功能；意味着通过IOUtil工具类可以完成对文件和Socket的读写能力。IOUtil会根据**FileDescriptor**所代表的类型通过转发器转发到对应底层来完成IO操作，图示如下：

![image-20200716102959755](.\images\image-20200716102959755.png)

在IOUtil工具类中，要用到一个叫做**iovec**的结构体用于保存待发送或者是接收到的数据，所以我们在正式介绍**IOUtil**之前，我们要先介绍一个类-**IOVecWrapper**，它是对**iovec**的Java封装形式。

## 一、IOVecWrapper类

### 1. iovec结构

iovec其实是描述的一个内存块结构，通过它我们可以获取到指定内存块指定长度的数据，其结构定义：

```c
typedef struct iovec {
    caddr_t  iov_base; 	// 指向一个内存块的首地址
    int      iov_len;	// 指向的内存块的长度
} iovec_t;
```

图示结构如下：

![image-20200716110017650](.\images\image-20200716110017650.png)

**IOVecWrapper**类就是对iovec结构的一个封装。

### 2. 分配

**IOVecWrapper**不能直接通过构造方法来创建，它提供了一个包访问域的静态方法，我们可以通过这个方法来获取IOVecWrapper实例，代码如下：

```java
private static final ThreadLocal<IOVecWrapper> cached = new ThreadLocal<IOVecWrapper>();
// size：要分配的iovec结构体的数量
static IOVecWrapper get(int size) {
    IOVecWrapper wrapper = cached.get(); // 首先从本地线程变量表里面获取缓存引用
    if (wrapper != null && wrapper.size < size) {
        // 如果之前分配过，但是比当前待分配的要小，我们就释放之前分配过的
        wrapper.vecArray.free();
        wrapper = null;
    }
    if (wrapper == null) {
        // 重新构建IOVecWrapper（分配iovec结构）
        wrapper = new IOVecWrapper(size);
        // 设置本地直接内存的回收机制，Deallocator内部也是调用的wrapper.vecArray.free()方法
        Cleaner.create(wrapper, new Deallocator(wrapper.vecArray));
        cached.set(wrapper);
    }
    return wrapper;
}
// 构造方法
private IOVecWrapper(int size) {
    this.size      = size;	// 表示要分配的iovec结构的个数
    this.buf       = new ByteBuffer[size];	// 初始化ByteBuffer,会用于接收内容
    this.position  = new int[size]; 	// 用于存储对应ByteBuffer的position值
    this.remaining = new int[size];		// 用于存储对应ByteBuffer的可用空间
    this.shadow    = new ByteBuffer[size];	// buf的一个复制
    /**
     * 分配size*SIZE_IOVEC大小的内存块空间
     * SIZE_IOVEC：表示一个iovec结构的大小，32位机器8个字节，64位机器16个字节
     * false：表示不需要跟内存页地址对齐
     */
    this.vecArray  = new AllocatedNativeObject(size * SIZE_IOVEC, false);
    this.address   = vecArray.address();	// 内存块首地址
}
```

:warning:这里分配的本地直接内存不受`MaxDirectMemory`的限制。

### 3. 回收

本地直接内存的回收一般都是使用的**Cleaner**方式来处理，在上面分配iovec的时候，我们也已经定义好了回收处理代码。

**Cleaner**机制的实现逻辑是在jvm进行垃圾回收的时候会回收虚引用对象，再引用处理器（ReferenceHandler）线程来回调***Cleaner.clean()***来实现的对本地内存的回收。

在上述分配**iovec**的时候，指定了一个Deallocator类，这个类就是会被回调的类，它的实现如下：

```java
private static class Deallocator implements Runnable {
    private final AllocatedNativeObject obj;
    Deallocator(AllocatedNativeObject obj) {
        this.obj = obj;
    }
    public void run() {	// 在clean()中的调用
        obj.free();	// 调用AllocatedNativeObject的释放内存方法来回收分配的内存
    }
}
```

## 二、IOUtil工具类

IOUtil是用于完成对底层文件描述符所代表对象的读写工作的，我们来看一下它的实现吧。在介绍具体的读写实现方法之前，先认识一下它的一个静态属性。

### 1. IOV_MAX值

这个静态属性的值是通过本地代码实现的初始化，用于控制在Scattering读和Gathering写的时候，读或写支持的最大的**iovec**结构数量，我们下面看一下它的定义和初始化方式：

```java
static final int IOV_MAX;

static {
    java.security.AccessController.doPrivileged(
        new java.security.PrivilegedAction<Void>() {
            public Void run() {
                // 系统本地动态库的加载
                // 一般位于jdk/jre/bin目录下，windows下对应的是net.dll和nio.dll
                System.loadLibrary("net");
                System.loadLibrary("nio");
                return null;
            }
        });

    initIDs();	// 在JNI中初始化FileDescriptor的fd属性位置

    /**
     * 初始化IOV_MAX，不同平台值可能会不一样
     * Windows下是16，Linux平台下先读取系统配置，如果没有配置，则也是16（在现在的Linux系统这个限制值可以达到1024）
     */
    IOV_MAX = iovMax();
}

static native int iovMax();
```

### 2. 读写实现

#### 写实现

IOUtil中对外提供了三种重载的写方式，用于支持不同的使用场景。

##### 第一种：一般写

```java
/**
 * 将src字节缓存中的所有内容写入到fd代表的底层对象中，并从底层对象的position位置开始写入新内容
 * position=-1：表示从开头写
 * position!=-1：表示从position字节处开始写
 * nd类型跟fd对应
 */
static int write(FileDescriptor fd, ByteBuffer src, long position,
                 NativeDispatcher nd)
    throws IOException
{
    if (src instanceof DirectBuffer)
        // 如果是直接本地内存则直接写
        return writeFromNativeBuffer(fd, src, position, nd);

    // src是jvm内存，走到这里
    int pos = src.position();
    int lim = src.limit();
    assert (pos <= lim);
    // 获取字节缓存池中写入的内容大小
    // lim-pos意味着在调用write之前，src要先执行src.flip()方法
    int rem = (pos <= lim ? lim - pos : 0);
    // 从DirectByteBuffer缓冲池中获取一个可以容纳src缓存内容的DirectByteBuffer对象
    // Util中的缓冲池也是一个IOV_MAX大小的循环队列，分配的个数达到最大之后不再继续分配新的字节缓存对象
    // Util中的缓冲池用到的本地直接内容是受MaxDirectMemory限制的
    ByteBuffer bb = Util.getTemporaryDirectBuffer(rem);
    try {
        bb.put(src);	// 从jvm堆内存拷贝到本地直接内存
        bb.flip();	// 翻转用于写入
        src.position(pos);

        // 调用从本地直接内存写数据的方法
        int n = writeFromNativeBuffer(fd, bb, position, nd);
        if (n > 0) {
            // 更新写入的字节数
            // 意味着在调用完write之后，我们可以直接调用src.compact()方法来防止未写完的数据被覆盖
            src.position(pos + n);
        }
        return n;	// 返回写入成功的字节数
    } finally {
        Util.offerFirstTemporaryDirectBuffer(bb); // 将本地直接内存回归到缓冲池中，便下次使用
    }
}
```

看一下***writeFromNativeBuffer()***方法的实现：

```java
// bb一定是DirectByteBuffer类型了
private static int writeFromNativeBuffer(FileDescriptor fd, ByteBuffer bb,
                                         long position, NativeDispatcher nd)
    throws IOException
{
    int pos = bb.position();
    int lim = bb.limit();
    assert (pos <= lim);
    int rem = (pos <= lim ? lim - pos : 0); // 待写内容的字节长度

    int written = 0;
    if (rem == 0)
        return 0;	// 代写内容为0，就直接返回
    if (position != -1) {
        // 本地方法：从底层文件描述符的指定位置开始写入内容
        written = nd.pwrite(fd,
                            ((DirectBuffer)bb).address() + pos,
                            rem, position);
    } else {
        // 本地方法：从底层文件描述符的开头写入内容
        written = nd.write(fd, ((DirectBuffer)bb).address() + pos, rem);
    }
    if (written > 0)
        bb.position(pos + written);
    return written;
}
```

我们看到最终是通过调用**NativeDispatcher**的相关写方法来将内容写入到文件描述符所代表对象中的，*nd*的具体类型是有外部控制的，比如在FileChannel中是传入的**FileDispatcher**类型的实例，在ServerSocketChannel中是传入的**SocketDispatcher**类型的实例。下面我们以FileDispatcher为例，看一下它的本地实现代码：

```c
// Linux平台下的本地实现

// 从开头写
// fdo->fd对象
// address->本地字节缓冲首地址
// len->本地字节缓冲的长度
JNIEXPORT jint JNICALL
Java_sun_nio_ch_FileDispatcherImpl_write0(JNIEnv *env, jclass clazz,
                              jobject fdo, jlong address, jint len)
{
    jint fd = fdval(env, fdo);	// 从FileDescriptor中获取文件描述符的值
    void *buf = (void *)jlong_to_ptr(address); // 地址转换
	// 通过write系统调用来将字节缓冲的内容写入到文件中
    // convertReturnVal是一个转换write结果的函数而已，JNI_FALSE表示的是写操作
    return convertReturnVal(env, write(fd, buf, len), JNI_FALSE);
}

// 从指定位置写
// offset->文件位置
JNIEXPORT jint JNICALL
Java_sun_nio_ch_FileDispatcherImpl_pwrite0(JNIEnv *env, jclass clazz, jobject fdo,
                            jlong address, jint len, jlong offset)
{
    jint fd = fdval(env, fdo);
    void *buf = (void *)jlong_to_ptr(address);
	// 通过pwrite64系统调用来将字节缓冲的内容写入到文件指定位置处
    return convertReturnVal(env, pwrite64(fd, buf, len, offset), JNI_FALSE);
}
```

Windows平台下的实现是通过***WriteFile()***实现的，这里不赘述了，感兴趣的可以下一个Openjdk源码自己研究一下。

##### 第二种：Gathering写

```java
/**
 * 将字节缓冲数组bufs中offset下标开始的length个字节缓冲的内容写入到fd文件描述符代表的对象中
 * 写入顺序是按照数组顺序依次写入的，同时这个写入也是原子操作的，要么都写入成功要么都失败，是不会被其他线程或进程打断的
 */
static long write(FileDescriptor fd, ByteBuffer[] bufs, int offset, int length,
                  NativeDispatcher nd)
    throws IOException
{
    // 通过IOVecWrapper分配length个iovec结构（用于指向待写的bufs中的字节缓冲内容）
    IOVecWrapper vec = IOVecWrapper.get(length);

    boolean completed = false;
    int iov_len = 0;
    try {

        int count = offset + length;	// 数组结尾下标截止处
        int i = offset;
        // 从offset开始循环迭代，最大允许迭代次数是IOV_MAX次
        // 也就是说如果length的值大于IOV_MAX，则一次也只能写IOV_MAX个ByteBuffer的内容
        // 这也是有些人会说，通过通过写内容的时候每次只能写16kb的内容，其实这个的直接原因还不是底层限制了
        // 只是限制了ByteBuffer的个数而已（而我们分配ByteBuffer的时候一般是以1kb来分配，这么一算就是16*1kb=16kb）
        // 所以如果想一次写多些内容，可以将分配的ByteBuffer的内容大一些，比如2kb
        // 不过一次写过大的内容对性能到底有没有影响还是要看实际的测试结果喽
        while (i < count && iov_len < IOV_MAX) {
            ByteBuffer buf = bufs[i];	// 获取数组bufs中下标i处的字节缓冲对象
            int pos = buf.position();
            int lim = buf.limit();
            assert (pos <= lim);
            int rem = (pos <= lim ? lim - pos : 0);	// 字节缓冲中的内容长度
            
            if (rem > 0) {
                // 将字节缓冲的相关属性记录到IOVecWrapper实例相关数组属性的iov_len下标处
                // 上面在介绍IOVecWrapper的时候其内部不是有buf、position、remaning等数组属性么
                vec.setBuffer(iov_len, buf, pos, rem);

                if (!(buf instanceof DirectBuffer)) {
                    // buf不是直接本地内存缓冲
                    // 还是从Util的字节缓冲池中获取一个本地内存字节缓冲实例
                    ByteBuffer shadow = Util.getTemporaryDirectBuffer(rem);
                    shadow.put(buf);	// 将字节缓冲内容从jvm堆拷贝到本地直接内存
                    shadow.flip();
                    // 将本地直接字节缓冲对象设置到IOVecWrapper的shadows数组属性的iov_len处
                    vec.setShadow(iov_len, shadow);
                    buf.position(pos);  // 记录一下写之前的position
                    buf = shadow;
                    pos = shadow.position();
                }
				
                // 为分配的iovec结构中的属性赋值，让其对应的指针指向DirectByteBuffer内存块
                vec.putBase(iov_len, ((DirectBuffer)buf).address() + pos);
                vec.putLen(iov_len, rem);
                iov_len++;	// 字节缓冲中内容不为0才会有加加操作
            }
            i++;
        }
        if (iov_len == 0)
            return 0L;	// 无内容要写直接返回

        // 本地方法调用，返回写入成功的字节数
        // fd：文件描述符对象，vec.address：iovec结构内存块的首地址，iov_len：iovec结构的个数
        long bytesWritten = nd.writev(fd, vec.address, iov_len);

        // Notify the buffers how many bytes were taken
        long left = bytesWritten;
        for (int j=0; j<iov_len; j++) {
            if (left > 0) {
                ByteBuffer buf = vec.getBuffer(j);	// 获取上面保存的第j个buffer
                int pos = vec.getPosition(j);
                int rem = vec.getRemaining(j);
                int n = (left > rem) ? rem : (int)left;
                buf.position(pos + n);	// 更新position，表示写入成功之后position的位置
                left -= n;
            }

            ByteBuffer shadow = vec.getShadow(j);
            if (shadow != null)
                // 将从缓冲池中取到的本地直接内存回归到缓冲池中
                Util.offerLastTemporaryDirectBuffer(shadow);
            vec.clearRefs(j);	// 引用置空
        }

        completed = true;	// 写入完成
        return bytesWritten;	// 写入成功的字节数

    } finally {
        if (!completed) {
            // 写入过程中出现了异常，资源释放
            for (int j=0; j<iov_len; j++) {
                ByteBuffer shadow = vec.getShadow(j);
                if (shadow != null)
                    Util.offerLastTemporaryDirectBuffer(shadow);
                vec.clearRefs(j);
            }
        }
    }
}
```

看一下这个本地方法***writev()***的实现吧：

```c
// Linux平台
JNIEXPORT jlong JNICALL
Java_sun_nio_ch_FileDispatcherImpl_writev0(JNIEnv *env, jclass clazz,
                                       jobject fdo, jlong address, jint len)
{
    jint fd = fdval(env, fdo);	// 获取文件描述符值
    struct iovec *iov = (struct iovec *)jlong_to_ptr(address); // iovec结构首地址
    // 系统调用writev，将iovec结构指向的内容写入到文件中
    return convertLongReturnVal(env, writev(fd, iov, len), JNI_FALSE);
}
```

这一个写的实现在Windows平台下有点细微差别，Windows平台下没有类似Linux平台下的writev函数，所以，在Windows平台下的实现是通过遍历iovec数组结构的方式，通过每次写入一个iovec来完成写入功能的，这就意味着在Windows平台下Gathering写不是原子操作的，可能会存在写入过程出现异常造成写入失败的情况。

##### 第三种：Gathering写

其实跟第二种是一样的，内部就是调用的第二种中的方法，代码如下：

```java
static long write(FileDescriptor fd, ByteBuffer[] bufs, NativeDispatcher nd)
    throws IOException
{
    return write(fd, bufs, 0, bufs.length, nd);
}
```

#### 读实现

跟写实现一样，IOUtil中也提供了三种读方式，分别如下：

##### 第一种：一般读

```java
/**
 * 将fd文件描述符指向的对象内容读到dst所代表的字节缓冲中，从fd对象的position位置开始读
 * position=-1：从开头开始读
 * position!=-1：从position位置开始读
 */
static int read(FileDescriptor fd, ByteBuffer dst, long position,
                NativeDispatcher nd)
    throws IOException
{
    if (dst.isReadOnly())
        // dst字节缓冲不允许写，抛出异常
        throw new IllegalArgumentException("Read-only buffer");
    if (dst instanceof DirectBuffer)
        // dst是本地直接内存，直接开始读
        return readIntoNativeBuffer(fd, dst, position, nd);

    // 说明dst不是本地直接内存
    
    // 从字节缓冲池中获取一个DirectByteBuffer对象
    ByteBuffer bb = Util.getTemporaryDirectBuffer(dst.remaining());
    try {
        // 将内容读到DirectByteBuffer中
        int n = readIntoNativeBuffer(fd, bb, position, nd);
        bb.flip();
        if (n > 0)
            // 将读到的内容从本地直接内存拷贝到jvm堆内存
            dst.put(bb);
        return n;
    } finally {
        // 将DirectByteBuffer回归池中
        Util.offerFirstTemporaryDirectBuffer(bb);
    }
}
```

看一下***readIntoNativeBuffer()***方法的实现：

```java
private static int readIntoNativeBuffer(FileDescriptor fd, ByteBuffer bb,
                                        long position, NativeDispatcher nd)
    throws IOException
{
    int pos = bb.position();
    int lim = bb.limit();
    assert (pos <= lim);
    int rem = (pos <= lim ? lim - pos : 0);

    if (rem == 0)
        return 0; // 字节缓冲可用空间为0，直接不读返回了
    int n = 0;
    if (position != -1) {
        // 本地方法，从指定位置开始读
        n = nd.pread(fd, ((DirectBuffer)bb).address() + pos,
                     rem, position);
    } else {
        // 本地方法，从开头位置读
        n = nd.read(fd, ((DirectBuffer)bb).address() + pos, rem);
    }
    if (n > 0)
        bb.position(pos + n);
    return n;
}
```

本地方法的实现：

```c
JNIEXPORT jint JNICALL
Java_sun_nio_ch_FileDispatcherImpl_read0(JNIEnv *env, jclass clazz,
                             jobject fdo, jlong address, jint len)
{
    jint fd = fdval(env, fdo);
    void *buf = (void *)jlong_to_ptr(address);

    return convertReturnVal(env, read(fd, buf, len), JNI_TRUE);
}

JNIEXPORT jint JNICALL
Java_sun_nio_ch_FileDispatcherImpl_pread0(JNIEnv *env, jclass clazz, jobject fdo,
                            jlong address, jint len, jlong offset)
{
    jint fd = fdval(env, fdo);
    void *buf = (void *)jlong_to_ptr(address);

    return convertReturnVal(env, pread64(fd, buf, len, offset), JNI_TRUE);
}
```

这些本地方法调用系统接口读写都是配套的方法，从手册都可以找到代表的意思，也都不再赘述，感兴趣的可自行研究研究。

##### 第二种：Scattering读

```java
static long read(FileDescriptor fd, ByteBuffer[] bufs, int offset, int length,
                 NativeDispatcher nd)
    throws IOException
{
    // 创建并分配用于接收读取内容的iovec结构本地内存
    IOVecWrapper vec = IOVecWrapper.get(length);

    boolean completed = false;
    int iov_len = 0;
    try {

        int count = offset + length;
        int i = offset;
        // 下面的实现跟write时是一样的，参考一下上面的注释就可以明白的
        while (i < count && iov_len < IOV_MAX) {
            ByteBuffer buf = bufs[i];
            if (buf.isReadOnly())
                throw new IllegalArgumentException("Read-only buffer");
            int pos = buf.position();
            int lim = buf.limit();
            assert (pos <= lim);
            int rem = (pos <= lim ? lim - pos : 0);

            if (rem > 0) {
                vec.setBuffer(iov_len, buf, pos, rem);

                if (!(buf instanceof DirectBuffer)) {
                    ByteBuffer shadow = Util.getTemporaryDirectBuffer(rem);
                    vec.setShadow(iov_len, shadow);
                    buf = shadow;
                    pos = shadow.position();
                }

                vec.putBase(iov_len, ((DirectBuffer)buf).address() + pos);
                vec.putLen(iov_len, rem);
                iov_len++;
            }
            i++;
        }
        if (iov_len == 0)
            return 0L;

        // 将fd指向的内容读到iovec结构指向的内存块中
        long bytesRead = nd.readv(fd, vec.address, iov_len);

        long left = bytesRead;
        for (int j=0; j<iov_len; j++) {
            ByteBuffer shadow = vec.getShadow(j);
            if (left > 0) {
                ByteBuffer buf = vec.getBuffer(j);
                int rem = vec.getRemaining(j);
                int n = (left > rem) ? rem : (int)left;
                if (shadow == null) {
                    // dsts中对应的buf本身就是本地直接内存
                    int pos = vec.getPosition(j);
                    buf.position(pos + n);
                } else {
                    // 将本地直接内存拷贝到jvm堆中
                    shadow.limit(shadow.position() + n);
                    buf.put(shadow);
                }
                left -= n;
            }
            if (shadow != null)
                // 回归缓冲池
                Util.offerLastTemporaryDirectBuffer(shadow);
            vec.clearRefs(j);
        }

        completed = true;
        return bytesRead;

    } finally {
        if (!completed) {
            // 读过程出现异常，资源回收
            for (int j=0; j<iov_len; j++) {
                ByteBuffer shadow = vec.getShadow(j);
                if (shadow != null)
                    Util.offerLastTemporaryDirectBuffer(shadow);
                vec.clearRefs(j);
            }
        }
    }
}
```

这里Linux平台下对应的本地方法就是调用的***readv()***系统调用接口，一样的是一个原子操作；而Windows平台对应的写方法一样，也是通过遍历iovec结构来循环读完成的Scattering读，也会存在一个原子性的问题（但是如果是在同一个java进程中，由于FileChannel中的读操作都是加了锁的，所以除非系统层面除了问题，不然在这个进程中还是有原子性的，对应的写操作也一样的；同时在FileChannel的open方法中OpenOption也没有提供共享读写的控制属性，所以如果通过FileChannel打开的文件默认都是不共享读写控制的，所以Windows系统层面的接口虽然没有提供原子功能，但是从整个java层面读写操作也还是不会被别的线程打断，但是由系统层面异常引起的问题还是会影响原子性的）。

##### 第三种：Scattering读

内部直接调用的第二种读，不再赘述。

### 3. 其他操作

#### 创建管道

只有在Linux平台下有用：

```java
// 返回的long类型的值，高32位和低32位分别代表的管道的两端的文件描述符的值，需要字节处理处理
// 底层是通过pipe()系统调用来创建的
static native long makePipe(boolean blocking);
```

#### 排空内容

```java
// 将fd文件描述符对象中没有读完的内容全部读完，会返回成功与否
// 底层调用的read()方法，每次读128个字节，直到读完结束
static native boolean drain(int fd) throws IOException;
```

#### 设置阻塞模式

```java
// 设置fd的阻塞模式
public static native void configureBlocking(FileDescriptor fd,
                                            boolean blocking)
    throws IOException;
```

还有其他一些设置文件描述的方法，不太重要就不说了。