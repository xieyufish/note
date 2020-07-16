# Java中对异步IO的抽象

什么是异步？我的理解：异步意味着你发出操作命令之后你可以继续做自己的事情，这个操作会自动完成预设的工作。就好像我在做菜的途中，按下了电饭煲做饭的按钮，就可以继续做我的菜而不用管做饭的事情，做饭这件事会自动进行下去。异步也意味着回调，在某个事情或某个状态发生的时候有个通知告诉我们，我们可以根据这个状态来做相关的处理；就像饭熟了，电饭煲会自动跳档并发声，我听到这个声音和跳档这个状态我就知道饭熟了可以拔插头了。

同理，那我们可以理解异步IO是什么呢？我们知道IO主要涉及读和写这两个操作，异步IO就意味着在读的时候，我只要发出一个读操作的信号，然后我接着做自己的事情，等这个读操作自动完成通知我们，然后我们根据这个完成状态做相应的处理；写操作也一样。那在Java中提供了怎样的抽象来为我们提供这样的异步IO功能呢？主要涉及如下三个关键接口：

1. AsynchronousChannel：异步通道接口；
2. CompletionHandler：完成处理器接口；
3. Future：将来任务接口。

下面我们来看看，Java中是如何定义这三个接口的。

## 一、AsynchronousChannel接口

![image-20200629152711801](\images\image-20200629152711801.png)

从类继承关系图，我们可以看出**AsynchronousChannel**继承自**Channel**接口，说明其是一个支持异步I/O操作的通道接口。

从jdk的规范可以知道，异步I/O操作支持调用形式为如下两种中的任意一种：

```java
Future<V> operation(...);
void operation(... A attachment, CompletionHandler<V, ? super A> handler);
```

其中的***operation***相应替换为对应的***read***或***write***即可。其中***V***表示I/O操作的结果，***A***表示I/O操作时的上下文对象。

在第一种调用形式中，我们可以通过**Future**接口中定义的方法来检查I/O操作是否完成、或者等待操作完成，然后检索操作的结果；而在第二种调用形式中，当一个I/O操作完成或者是失败的时候是通过调用一个**CompletionHandler**实例来处理操作结果的。

在接口定义中也有说明，对于**AsynchronousChannel**接口的任何实现必须是可异步关闭的；意思就是说：如果在一个通道上有正在进行的I/O操作，而此时这个通道的***close()***方法被调用，那么这个正在进行的I/O操作必须以抛出**AsynchronousCloseException**异常而失败。同时，**AsynchronousChannel**是线程安全的类。有些实现可以并行支持读和写操作，但绝不允许在同一时刻同时发出多个读或者多个写。

### 关于取消

我们知道在**Future**接口中定义了一个***cancel()***方法用于取消操作的执行。如果我们调用由**AsynchronousChannel**实例返回的**Future**实例的***cancel***方法，这个操作将造成所有在等待I/O操作结果的线程抛出**CancellationException**或者**AsynchronousCloseException**（取决于cancel方法的调用参数是否为true，以及I/O操作是否完成）。

## 二、CompletionHandler接口

一个用户消费异步I/O操作结果的处理器。这个接口定义就提供了两个方法，分别用于处理异步I/O操作成功和失败的情况，我们看一下具体定义如下：

```java
/**
 * V：代表期望的操作的结果对象类型
 * A：代表I/O操作的上下文对象
 */
public interface CompletionHandler<V,A> {

    /**
     * 当一个I/O操作完成的时候被调用
     */
    void completed(V result, A attachment);

    /**
     * 当一个I/O操作失败的时候被调用
     */
    void failed(Throwable exc, A attachment);
}
```

## 三、Future接口

一个**Future**代表的是一个异步计算的结果；它的方法主要用于：检查计算是否完成、等待计算完成以及检索计算的结果。我们看一下具体的定义如下：

```java
/**
 * V：代表期望的结果类型
 */
public interface Future<V> {

    /**
     * 试图取消这个计算任务的执行。
     *
     * 在如下情况，将取消失败：
     * 1. 计算任务已经完成；
     * 2. 计算任务已经被取消；
     * 3. 由于其他原因不能被取消。
     *
     * 如果取消成功，非如下情况：
     * 1. 如果任务没有开始执行，那么这个任务永远都不会执行；
     * 2. 如果任务已经开始执行，由参数mayInterruptIfRunning的值来决定任务是否可以被中断，如果为true，则会试图中断这个任务线程的执行，否则不会试图中断。
     *
     * 在这个方法返回之后，isDone方法总是返回true，而isCancelled方法只有在这个方法返回为true的时候才返回true。
     */
    boolean cancel(boolean mayInterruptIfRunning);

    /**
     * 在cancel返回true的时候返回true
     */
    boolean isCancelled();

    /**
     * 判断这个计算任务是否完成
     */
    boolean isDone();

    /**
     * 等待这个计算任务完成，然后检索它的计算结果。
     * 如果计算任务被取消则抛出CancellationException异常；
     * 如果计算任务发生异常则抛出ExecutionException异常；
     * 如果当前线程在等待任务完成的过程中被中断则抛出InterruptedException异常。
     */
    V get() throws InterruptedException, ExecutionException;

    /**
     * 等待这个计算任务最多timeout的时间，如果在此等待时间内完成则返回结果，否则抛出超时异常。
     * 如果计算任务被取消则抛出CancellationException异常；
     * 如果计算任务发生异常则抛出ExecutionException异常；
     * 如果当前线程在等待任务完成的过程中被中断则抛出InterruptedException异常。
     */
    V get(long timeout, TimeUnit unit)
        throws InterruptedException, ExecutionException, TimeoutException;
}
```

至此，Java中关于异步I/O的抽象已经讲完，我们可以感觉到Java对异步I/O的抽象是真的很抽象，我们甚至都感觉不到它的异步是怎么体现的，就用一个简单的接口概括了所有。在后续文章中，我会介绍Java中对异步I/O的具体实现：网络异步I/O和文件异步I/O。