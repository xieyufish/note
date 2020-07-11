# Java中的文件IO

Java发展到现在，其JDK中关于操作文件IO的接口更新换代已经有三种，分别如下：

1. 第一代：**FileInputStream**和**FileOutputStream**，分别用于写文件和读文件操作，且是阻塞的；
2. 第二代：**FileChannel**，一个接口就可用于写文件和读文件操作，也是阻塞的，但是提供了额外的零拷贝特性；
3. 第三代：**AsynchronousFileChannel**，一个接口即可以用于写文件也可以用于读文件，异步的。

下面我们就先来认识一下第一代的实现原理吧。

## 一、FileInputStream和FileOutputStream

**FileInputStream**和**FileOutputStream**，以字节流的方式来操作文件内容；严格来说，应该还有**FileReader**和**FileWriter**这个两个类可以用于读写文件（以字符流的方式），但其实这两个类的内部实现其实也还是通过FileInputStream和FileOutputStream来读写文件，只不过在其上包装了一层StreamEncoder和StreamDecoder来将字节转换为字符，所以我们了解FileInputStream和FileOutputStream就足够了。

### 1. FileInputStream

从指定的文件中读取字节数据。在实例化的时候会打开指定的文件系统上的某个文件，内部通过一个`FileDescriptor fd`属性来维持对底层打开文件的引用。**FileInputStream**主要提供如下四种能力：

- 打开文件系统中的一个指定文件；
- 从打开的文件中读取字节内容；
- 跳到打开文件的指定字节位置处；
- 查看打开文件还有多少字节可读。

下面，我们分别来看一下Java中是如何实现这四种功能的。

> **打开文件**

:warning:**注意：**这里的打开文件意味着是把文件的控制块读取到内存中，并不是跟我们用文本编辑器那样打开一个文件（这种打开除了打开其实已经读取了文件内容了）。

打开文件是在构造**FileInputStream**实例的时候根据传入的文件路径来完成的打开操作，其构造函数会调用一个***open(name)***的方法：

```java
private native void open0(String name) throws FileNotFoundException;

// open方法然后通过一个本地方法来完成打开文件的操作
private void open(String name) throws FileNotFoundException {
    open0(name);
}
```

从这个方法定义，我们不能传入控制文件访问权限、共享模型、文件属性等这样的特性来自定义文件操作，这些都由底层写死了，我们没办法，这也是FileInputStream不灵活的地方。

这个***open0()***本地方法在Windows平台下的最终实现如下（只列出核心代码）：

```c
/**
 * path：文件路径
 * flags：控制标签，这里由本地代码限制了，在FileInputStream中值为：O_RDONLY=0
 * 在FileOutputStream中值为：O_WRONLY | O_CREAT | (append ? O_APPEND : O_TRUNC)
 */
FD
winFileHandleOpen(JNIEnv *env, jstring path, int flags)
{
    // 控制文件的访问权限，将传入的值转换为Windows下的标准访问权限值
    // access最终等于GENERIC_READ，读权限
    const DWORD access =
        (flags & O_WRONLY) ?  GENERIC_WRITE :
        (flags & O_RDWR)   ? (GENERIC_READ | GENERIC_WRITE) :
        GENERIC_READ;
    
    // 控制文件的共享模式，意思是允许和其他进程同时操作这个文件的方式
    // 这里是允许多个进程对这个文件读和写
    const DWORD sharing =
        FILE_SHARE_READ | FILE_SHARE_WRITE;
    
    // 控制文件的操作方式，比如文件不存在的时候是创建一个还是只允许打开已经存在的等
    // 这里是OPEN_EXISTING，只允许打开已经存在的文件，否则报FileNotFoundException
    const DWORD disposition =
        (flags & O_TRUNC) ? CREATE_ALWAYS :
        (flags & O_CREAT) ? OPEN_ALWAYS   :
        OPEN_EXISTING;
    
    // 控制文件同步方式，如果指定了O_SYNC或O_DSYNC，除了会写入系统缓存之外，还会直接落盘
    // 在FileChannel和AsynchronousFileChannel可以指定
    const DWORD  maybeWriteThrough =
        (flags & (O_SYNC | O_DSYNC)) ?
        FILE_FLAG_WRITE_THROUGH :
        FILE_ATTRIBUTE_NORMAL;
    
    // 控制关闭文件时的操作，如果是临时文件会执行删除
    // 这里是FILE_ATTRIBUTE_NORMAL，正常关闭
    const DWORD maybeDeleteOnClose =
        (flags & O_TEMPORARY) ?
        FILE_FLAG_DELETE_ON_CLOSE :
        FILE_ATTRIBUTE_NORMAL;
    
    // 组合成文件属性参数
    const DWORD flagsAndAttributes = maybeWriteThrough | maybeDeleteOnClose;
    HANDLE h = NULL;
	
    // 自定义函数将path转成标准的NT文件路径
    WCHAR *pathbuf = pathToNTPath(env, path, JNI_TRUE);
    if (pathbuf == NULL) {
        return -1;
    }
    
    // Windows标准函数库方法调用，正式打开创建一个文件句柄
    h = CreateFileW(
        pathbuf,            /* 文件路径 */
        access,             /* 读写权限 */
        sharing,            /* 共享标记 */
        NULL,               /* 安全属性 */
        disposition,        /* 创建标记 */
        flagsAndAttributes, /* 标记属性等 */
        NULL);
    
    free(pathbuf); // 释放pathBuf占用的内存

    if (h == INVALID_HANDLE_VALUE) {
        // 创建失败，抛出异常
        throwFileNotFoundException(env, path);
        return -1;
    }
    return (jlong) h;	// 返回文件句柄，会将这个h值设置到FileDescriptor对象的fd属性中
}
```

而在linux操作系统下的实现如下：

```c
/**
 * path：文件路径
 * oflag：访问文件模式，在FileInputStream中是O_RDONLY，
 * FileOutputStream中是：O_WRONLY | O_CREAT | (append ? O_APPEND : O_TRUNC)
 * mode：0666，操作文件的权限
 */
FD
handleOpen(const char *path, int oflag, int mode) {
    FD fd;
    
    // RESTARTABLE是定义的一个宏，表示将第一个参数的执行结果赋值给第二个参数
    // 通过库函数open64的调用来打开一个文件，将结果文件描述符赋值给fd
    RESTARTABLE(open64(path, oflag, mode), fd);
    if (fd != -1) {
        struct stat64 buf64;
        int result;
        // 获取打开文件的状态值，fstat64的调用
        RESTARTABLE(fstat64(fd, &buf64), result);
        if (result != -1) {
            if (S_ISDIR(buf64.st_mode)) {
                // 如果打开的文件是一个目录，则关闭文件描述符，并返回错误信息
                // Windows下不用这个判断的原因是：CreateFileW函数只能打开文件
                // 打开目录是CreateDirectory函数
                close(fd);
                errno = EISDIR;
                fd = -1;
            }
        } else {
            close(fd);
            fd = -1;
        }
    }
    return fd;
}
```

> **读文件内容**

**FileInputStream**中提供了多个读文件内容的重载方法，如下：

```java
// 一次读取一个字节，作为结果返回
public int read() throws IOException {
    return read0(); // 通过本地方法实现
}
// 一次读取多个字节，放在b[]字节数组中，返回读取的字节数
public int read(byte b[]) throws IOException {
    return readBytes(b, 0, b.length); // 通过本地方法实现
}
// 一次读取多个字节，放在b[]字节数组的off~len-1的位置，返回读取的字节数
public int read(byte b[], int off, int len) throws IOException {
    return readBytes(b, off, len); // 通过本地方法实现
}
// 本地方法，每次读取一个字节
private native int read0() throws IOException;
// 本地方法，每次读取多个字节，将读取内容放置到字节数组的off~len-1的位置
private native int readBytes(byte b[], int off, int len) throws IOException;
```

两个本地方法的最终实现都是通过同一个方法来完成，Windows平台的处理如下：

```c
/**
 * fd：指向文件的句柄
 * buf：字节数组，用于存放读取内容的字节数组首地址，注意这里的字节数组是本地方法在本地堆重新分配的
 * len：预读取的字节长度
 */
JNIEXPORT
jint
handleRead(FD fd, void *buf, jint len)
{
    DWORD read = 0;
    BOOL result = 0;
    HANDLE h = (HANDLE)fd;
    if (h == INVALID_HANDLE_VALUE) {
        return -1;
    }
    // 通过Windows库函数ReadFile从文件中读取指定内容
    // 此函数在操作文件时的返回条件为：
    // 1. 读取到指定长度的字节数（意味着没有读取到指定长度会一直阻塞）
    // 2. 读取到了文件的末尾
    result = ReadFile(h,          /* 文件句柄 */
                      buf,        /* 存放读取内容的字节数组 */
                      len,        /* 准备读取的字节数 */
                      &read,      /* 存放实际读取了多少字节数的地址 */
                      NULL);      /* overlapped结构，在异步文件IO才有用 */
    if (result == 0) {
        int error = GetLastError();
        if (error == ERROR_BROKEN_PIPE) {
            return 0; /* 针对管道，读取到了末尾 */
        }
        return -1;
    }
    return (jint)read;	// 返回实际读取的字节数
}
```

Linux平台的实现如下：

```c
/**
 * fd：文件描述符
 * buf：字节数组，用于存放读取内容的字节数组首地址，注意这里的字节数组是本地方法在本地堆重新分配的
 * len：预读取的字节长度
 */
ssize_t
handleRead(FD fd, void *buf, jint len)
{
    ssize_t result;
    // 通过read函数来完成读取（会阻塞）
    RESTARTABLE(read(fd, buf, len), result);
    return result;
}
```

不管哪个平台，读取完成之后，会将buf字节数组的内容拷贝到java方法传进去的b所指向的字节数组，用图来表示一下这个过程吧：

![image-20200710160134177](.\images\image-20200710160134177.png)

关于操作系统文件缓存的问题，可以[参考这篇文章](.\windows中的文件缓存.md)。系统缓存管理器读的时候会先检查系统缓存中是否有了这个文件内容，有的话就不会再去读盘。

:warning:如果是调用的read(b[])这种读多个字节的方法，在执行本地方法的时候，会预分配一个8kb大小的栈内存给buf，如果需要读取的内容长度超过8kb，即b[]的长度超过8192，则本地方法会在进程的本地堆内存中分配一个指定长度的内容给buf（此时分配失败会抛出内存溢出的异常）。

> **跳到文件指定位置**

这个方法我也不常用，一直还没注意有这样的一个方法，我们看一下它的定义：

```java
/**
 * 跳过或丢弃n个字节，直接就是一个本地方法
 * n>0：向前移
 * n<0：往回移
 */
public native long skip(long n) throws IOException;
```

我们看一下其在Windows平台下的实现：

```c
/**
 * fd：文件句柄
 * offset：就是n值
 * whence：SEEK_CUR
 */
jlong
handleLseek(FD fd, jlong offset, jint whence)
{
    LARGE_INTEGER pos, distance;
    DWORD lowPos = 0;
    long highPos = 0;
    DWORD op = FILE_CURRENT;
    HANDLE h = (HANDLE)fd;

    if (whence == SEEK_END) {
        op = FILE_END; 	// 起点为文件末尾
    }
    if (whence == SEEK_CUR) {
        op = FILE_CURRENT; // 起点为当前位置
    }
    if (whence == SEEK_SET) {
        op = FILE_BEGIN; // 起点为文件开头
    }

    distance.QuadPart = offset; // 记录要跳过的字节数
    // windows库函数调用，从op规定的位置开始跳
    // 将跳完之后的文件指针位置存放在pos中
    if (SetFilePointerEx(h, distance, &pos, op) == 0) {
        return -1;
    }
    return long_to_jlong(pos.QuadPart);
}
```

Linux平台下的时候就是标准库函数***lsseek()***。具体可以去查看这个函数的使用。

> **可读字节查看**

```java
public native int available() throws IOException;
```

Windows实现：

```c
/**
 * fd：文件句柄
 * pbytes：存储结果的指针
 */
int
handleAvailable(FD fd, jlong *pbytes) {
    HANDLE h = (HANDLE)fd;
    DWORD type = 0;

    type = GetFileType(h);
    /* 如果文件句柄指向的是键盘或者是管道 */
    if (type == FILE_TYPE_CHAR || type == FILE_TYPE_PIPE) {
        int ret;
        long lpbytes;
        HANDLE stdInHandle = GetStdHandle(STD_INPUT_HANDLE);
        if (stdInHandle == h) {
            ret = handleStdinAvailable(fd, &lpbytes); /* 键盘 */
        } else {
            ret = handleNonSeekAvailable(fd, &lpbytes); /* 管道 */
        }
        (*pbytes) = (jlong)(lpbytes);
        return ret;
    }
    /* 文件 */
    if (type == FILE_TYPE_DISK) {
        jlong current, end;

        LARGE_INTEGER filesize;
        current = handleLseek(fd, 0, SEEK_CUR); // 首先获取文件指针当前所在的位置
        if (current < 0) {
            return FALSE;
        }
        // 获取文件的长度
        if (GetFileSizeEx(h, &filesize) == 0) {
            return FALSE;
        }
        end = long_to_jlong(filesize.QuadPart);
        *pbytes = end - current; // 文件长度减去当前位置即为还可以读取的内容长度
        return TRUE;
    }
    return FALSE;
}
```

Linux平台下：

```c
jint
handleAvailable(FD fd, jlong *pbytes)
{
    int mode;
    struct stat64 buf64;
    jlong size = -1, current = -1;

    int result;
    RESTARTABLE(fstat64(fd, &buf64), result); // 获取文件状态属性
    if (result != -1) {
        mode = buf64.st_mode;
        // 如果是字符设备、FIFO文件（一般是管道）、SOCKET文件
        if (S_ISCHR(mode) || S_ISFIFO(mode) || S_ISSOCK(mode)) {
            int n;
            int result;
            RESTARTABLE(ioctl(fd, FIONREAD, &n), result); // FIONREAD读取内容操作
            if (result >= 0) {
                *pbytes = n;
                return 1;
            }
        } else if (S_ISREG(mode)) { // 是一个常规文件
            size = buf64.st_size;	// 文件状态属性中有保存文件长度的属性
        }
    }

    // 获取文件指针当前所在位置
    if ((current = lseek64(fd, 0, SEEK_CUR)) == -1) {
        return 0;
    }

    if (size < current) {
        if ((size = lseek64(fd, 0, SEEK_END)) == -1)
            return 0;
        else if (lseek64(fd, current, SEEK_SET) == -1)
            return 0;
    }

    *pbytes = size - current; // 差值表示还可读内容的大小
    return 1;
}
```

### 2. FileOutputStream

将内容写入到指定的文件中。同样的也是在创建FileOutputStream实例的时候，打开一个文件，并通过`FileDescriptor fd`来维护这个文件引用。此类提供的主要功能就是写内容到文件，并没有其他额外的功能。

**FileOutputStream**打开文件的方式与**FileInputStream**有些许差别，其提供了一个额外的参数***append***来控制是在文件后面追加写还是覆盖写。我们看一下它的定义：

```java
/**
 * append：true，表示追加写；false，表示覆盖写
 * 在文件不存在的情况下会创建一个新文件
 */
private void open(String name, boolean append)
    throws FileNotFoundException {
    open0(name, append);
}

/**
 * 它跟FileInputStream里面的open0方法一样，是调用的同一个本地方法，只不过传入的参数有差别，上面解析的时候有说
 */
private native void open0(String name, boolean append)
        throws FileNotFoundException;
```

我们主要来看一下它的写方法吧，在**FileOutputStream**中也是有多个重写的***write()***方法可以让我们来完成写内容到文件中的工作，具体如下：

```java
/**
 * 将低8位写入到文件中，高24位将会被忽略掉
 */
public void write(int b) throws IOException {
    write(b, append);	// 调用本地方法
}
/**
 * 将字节数组的所有内容写入到文件中
 */
public void write(byte b[]) throws IOException {
    writeBytes(b, 0, b.length, append);
}
/**
 * 将字节数组off~len-1的内容写入到文件中
 */
public void write(byte b[], int off, int len) throws IOException {
    writeBytes(b, off, len, append);
}

private native void write(int b, boolean append) throws IOException;

private native void writeBytes(byte b[], int off, int len, boolean append)
        throws IOException;
```

***write***跟上面的read函数一样，本地实现最终都是由同一个函数来完成。

现在我们来看一下Windows平台下的实现（只是列出了核心的代码，有一些判断啊之类的没有列出来）：

```java
/**
 * fd：文件句柄
 * buf：指向待写入内容，这个也是将jvm堆拷贝到本地堆之后的指针
 * len：表示写入的长度
 */
jint handleWrite(FD fd, const void *buf, jint len) {
    return writeInternal(fd, buf, len, JNI_FALSE); // append始终为false，最终的控制由在打开文件时指定的append属性来完成的
}

static jint writeInternal(FD fd, const void *buf, jint len, jboolean append)
{
    BOOL result = 0;
    DWORD written = 0;
    HANDLE h = (HANDLE)fd;
    if (h != INVALID_HANDLE_VALUE) {	// 文件句柄合法
        OVERLAPPED ov;
        LPOVERLAPPED lpOv;
        if (append == JNI_TRUE) {
            // 不会走这分支
            ov.Offset = (DWORD)0xFFFFFFFF;
            ov.OffsetHigh = (DWORD)0xFFFFFFFF;
            ov.hEvent = NULL;
            lpOv = &ov;
        } else {
            lpOv = NULL;
        }
        // Windows库函数，阻塞直到写完所有内容或发生异常
        result = WriteFile(h,                /* 文件句柄 */
                           buf,              /* 指向待写入内容的指针 */
                           len,              /* 待写入内容的字节长度 */
                           &written,         /* 存放实际写入的内容长度 */
                           lpOv);            /* 异步IO时用 */
    }
    if ((h == INVALID_HANDLE_VALUE) || (result == 0)) { // 0表示写失败
        return -1;
    }
    return (jint)written;	// 返回实际写入的内容长度
}
```

Linux平台下的实现：

```c
/**
 * fd：文件句柄
 * buf：指向待写入内容，这个也是将jvm堆拷贝到本地堆之后的指针
 * len：表示写入的长度
 */
ssize_t
handleWrite(FD fd, const void *buf, jint len)
{
    ssize_t result;
    // 直接调用库函数write
    RESTARTABLE(write(fd, buf, len), result);
    return result;
}
```

这里写的过程跟读一样，如果是用的***write(b[])***这种写多个字节的方法，那么在本地方法执行的时候也会先预分配一个8kb大小的栈空间，如果写的内容超过了8kb大小会分配一个指定大小的堆空间来拷贝b[]（可能会抛出内存溢出的异常）。用图来表示写过程的内容流转情况，如下：

![image-20200710173807175](.\images\image-20200710173807175.png)

:warning:这里写只会先写到系统的文件缓存中，并不会直接落盘的，最终落盘的操作时机是由缓存管理器来决定的。当然如果我们在写完所有内容之后，想确保落盘，我们可以调用如下代码执行真正的斜盘操作：

```java
FileOutputStream fos = new FileOutputStream(...);
fos.write(...);
// 执行同步到磁盘的操作，注意这一个操作会阻塞，一直等到所有数据落盘之后才会返回，
// 给人的直观感受性能下降4倍左右，且磁盘写速度会跑满
fos.getFD().sync();
```

### 3. 总结

可以看到，不管是读还是写；文件内容的流转都经过了两次拷贝之后，才能完成最终的操作，显然这种方式对与读写的效率影响是非常大的；后续我们会看到**FileChannel**对这种拷贝情况的优化。

从**FileInputStream**和**FileOutputStream**的实现来看，我们对每次读写操作的优化工作是无法进行下去的，我们能做的就是尽量的减少读或者是写的次数，所以我们一般会在FileInputStream和FileOutputStream的外面包装一层**BufferedInputStream**和**BufferedOutputStream**，也就是在外面包装一层缓存层，只有在缓存层满了之后才会执行最终的写操作，每次读的时候都是读缓存层大小的内容，而不是单个字节单个字节的读以减少读操作的次数。一般通过Buffered封装减少实际的读写次数（其实就是减少用户空间到内核空间的切换）之后，性能提升在4倍左右。