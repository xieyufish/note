## java源码解析-Thread类

**Thread：**java中的线程类, 实现了Runnable接口(只有一个run()方法)。在java中创建一个线程的方式有：可以new一个Thread或者继承Thread或者实现Runnable接口(并传递给Thread对象)。

Thread类中定义了线程的优先级分为十个等级，从1到10，默认新建线程的优先级为5。源码如下：

```java
/**
* The minimum priority that a thread can have.
*/
public final static int MIN_PRIORITY = 1;

/**
* The default priority that is assigned to a thread.
*/
public final static int NORM_PRIORITY = 5;

/**
* The maximum priority that a thread can have.
*/
public final static int MAX_PRIORITY = 10;
```

在Thread中包含有一个Runnable类型的实例变量target，Thread线程启动执行时，就是运行的target中run方法里面的内容。

### 核心方法

> init方法，初始化一个线程，在new Thread()的时候，会调用

```java
private void init(ThreadGroup g, Runnable target, String name,
                  long stackSize) {
  init(g, target, name, stackSize, null);
}

private void init(ThreadGroup g, Runnable target, String name,
                      long stackSize, AccessControlContext acc) {
      // 线程的名字不能为空, 如果在new的时候没有传入线程名字name, 那么将使用"Thread-" + nextThreadNum() --- Thread中的同步静态方法,实现递增  这种方式命名
        if (name == null) {       
            throw new NullPointerException("name cannot be null");
        }

        this.name = name.toCharArray(); //设置线程名字
		
  		//获取当前正在执行的线程(也就是在执行 new Thread()操作的线程)作为本线程的父线程
        Thread parent = currentThread();      
        SecurityManager security = System.getSecurityManager();          //获取系统安全管理器
        if (g == null) {                      //如果传进来的线程组对象为空,
            /* Determine if it's an applet or not */

            /* If there is a security manager, ask the security manager
               what to do. */
          	//获取系统安全管理器所在的线程组, 其实就是当前正在执行线程所在的线程组
            if (security != null) {
                g = security.getThreadGroup();      //security.getThreadGroup()的实现代码:  
            }

            /* If the security doesn't have a strong opinion of the matter
               use the parent thread group. */
            if (g == null) {   //如果安全管理器为空, 那么就跟父线程在同一个线程组
                g = parent.getThreadGroup();
            }
        }

        /* checkAccess regardless of whether or not threadgroup is
           explicitly passed in. */
        g.checkAccess();

        /*
         * Do we have the required permissions?
         */
        if (security != null) {
            if (isCCLOverridden(getClass())) {
                security.checkPermission(SUBCLASS_IMPLEMENTATION_PERMISSION);
            }
        }

        g.addUnstarted();          //线程组中未开始线程数 + 1

        this.group = g;         //设置本线程所在线程组
        this.daemon = parent.isDaemon();    //设置本线程是否后台线程, 如果父线程是后台线程那么本线程也是, 也就是说一个线程是否后台线程跟父线程(也就是创建他的线程)有关
        this.priority = parent.getPriority();     //暂时设置本线程的优先级为父线程的优先级, 下面会做修改
        if (security == null || isCCLOverridden(parent.getClass()))
            this.contextClassLoader = parent.getContextClassLoader();
        else
            this.contextClassLoader = parent.contextClassLoader;
        this.inheritedAccessControlContext =
                acc != null ? acc : AccessController.getContext();
        this.target = target;        //设置本线程的实际执行体, 由调用传入
        setPriority(priority);       //设置线程优先级, 如果参数priority的值大于所在线程组设置的最大优先级, 那么重新设置本线程优先级为线程组的最大优先级, 否则不变. 也就是说线程的优先级不能大于所在线程组设置的最大优先级
        if (parent.inheritableThreadLocals != null)
            this.inheritableThreadLocals =
                ThreadLocal.createInheritedMap(parent.inheritableThreadLocals);
        /* Stash the specified stack size in case the VM cares */
        this.stackSize = stackSize;      //线程栈的大小

        /* Set thread ID */
        tid = nextThreadID();     //线程id
    }      
```

> start()方法，start方法中实际调用的是本地方法start0

```java
public synchronized void start() {
  /**
         * This method is not invoked for the main method thread or "system"
         * group threads created/set up by the VM. Any new functionality added
         * to this method in the future may have to also be added to the VM.
         *
         * A zero status value corresponds to state "NEW".
         */
  if (threadStatus != 0)
    throw new IllegalThreadStateException();

  /* Notify the group that this thread is about to be started
         * so that it can be added to the group's list of threads
         * and the group's unstarted count can be decremented. */
  //加入到当前线程组中
  group.add(this);

  boolean started = false;
  try {
    start0();
    started = true;
  } finally {
    try {
      if (!started) {
        group.threadStartFailed(this);        //  如果线程开始失败, 那么就会从线程组中移除
      }
    } catch (Throwable ignore) {
      /* do nothing. If start0 threw a Throwable then
                  it will be passed up the call stack */
    }
  }
}

private native void start0();
```

> run()方法，实际执行target对象的run()方法

```java
@Override
public void run() {
  if (target != null) {
  	target.run();
  }
}
```

### 线程状态

```java
public enum State {
  /**
  * Thread state for a thread which has not yet started.
  */
  NEW,　　　　//线程创建还没有开始, 算是就绪状态吧

    /**
    * Thread state for a runnable thread.  A thread in the runnable
    * state is executing in the Java virtual machine but it may
    * be waiting for other resources from the operating system
    * such as processor.
    */
    RUNNABLE,     // 运行时状态

    /**
    * Thread state for a thread blocked waiting for a monitor lock.
    * A thread in the blocked state is waiting for a monitor lock
    * to enter a synchronized block/method or
    * reenter a synchronized block/method after calling
    * {@link Object#wait() Object.wait}.
    */
    BLOCKED,     //  堵塞

    /**
    * Thread state for a waiting thread.
    * A thread is in the waiting state due to calling one of the
    * following methods:
    * <ul>
    *   <li>{@link Object#wait() Object.wait} with no timeout</li>
    *   <li>{@link #join() Thread.join} with no timeout</li>
    *   <li>{@link LockSupport#park() LockSupport.park}</li>
    * </ul>
    *
    * <p>A thread in the waiting state is waiting for another thread to
    * perform a particular action.
    *
    * For example, a thread that has called <tt>Object.wait()</tt>
    * on an object is waiting for another thread to call
    * <tt>Object.notify()</tt> or <tt>Object.notifyAll()</tt> on
    * that object. A thread that has called <tt>Thread.join()</tt>
    * is waiting for a specified thread to terminate.
    */
    WAITING,   // 等待

    /**
    * Thread state for a waiting thread with a specified waiting time.
    * A thread is in the timed waiting state due to calling one of
    * the following methods with a specified positive waiting time:
    * <ul>
    *   <li>{@link #sleep Thread.sleep}</li>
    *   <li>{@link Object#wait(long) Object.wait} with timeout</li>
    *   <li>{@link #join(long) Thread.join} with timeout</li>
    *   <li>{@link LockSupport#parkNanos LockSupport.parkNanos}</li>
    *   <li>{@link LockSupport#parkUntil LockSupport.parkUntil}</li>
    * </ul>
    */
    TIMED_WAITING,   //有时间限制的等待

    /**
    * Thread state for a terminated thread.
    * The thread has completed execution.
    */
    TERMINATED;   //终止
}
```