## java源码解析-FutureTask类

**FutureTask**是从jdk5之后创建的属于java.util.concurrent包下的一个类。

**FutureTask：** 一个可取消执行的异步任务类。这个类提供了Future接口的基本实现，能够开始和取消执行异步任务，也可以查看执行任务是否完成，检索执行结果等。 
要注意的是: get()方法获取执行结果会阻塞直到任务执行完成，一旦一个任务已经完成就不能再次开始和结束(除非执行时通过runAndReset()方法).

### 类定义

```java
/**
* FutureTask实现的是RunnableFuture接口, 而RunnableFuture接口是继承自Runnable和Future两个接口的.
* 所以FutureTask他同时拥有Runnable和Future的特性,这也就意味着他的对象可以传递给线程执行;
* 同时, jdk中也提供了Executor接口来执行FutureTask(因为Executor也可以执行Runnable对象).
**/
public class FutureTask<V> implements RunnableFuture<V>
```

### 成员变量
| 修饰符                       | 变量名              | 作用                                   |
| ------------------------- | ---------------- | ------------------------------------ |
| private volatile int      | state            | 当前异步任务的状态                            |
| private Callable\<V>      | callable         | 任务的执行体,具体要做的事                        |
| private Object            | outcome          | 任务的执行结果存储对象(get()方法的返回值)             |
| private volatile Thread   | runner           | 任务的执行线程                              |
| private volatile WaitNode | waiters          | 获取任务结果的等待线程(是一个链式列表)                 |
| private static final int  | NEW = 0          | 任务初始化状态                              |
| private static final int  | COMPLETING = 1   | 任务正在完成状态(任务已经执行完成,但是结果还没有赋值给outcome) |
| private static final int  | NORMAL = 2       | 任务完成(结果已经赋值给outcome)                 |
| private static final int  | EXCEPTIONAL = 3  | 任务执行异常                               |
| private static final int  | CANCELLED = 4    | 任务被取消                                |
| private static final int  | INTERRUPTING = 5 | 任务被中断中                               |
| private static final int  | INTERRUPTED  = 6 | 任务被中断                                |

WaitNode的定义：
```java
static final class WaitNode {
    volatile Thread thread;
    volatile WaitNode next;
    WaitNode() { thread = Thread.currentThread(); }
}
```

异步任务可能的状态之间的转换：
> NEW -> COMPLETING -> NORMAL       
> NEW -> COMPLETING -> EXCEPTIONAL  
> NEW -> CANCELLED                  
> NEW -> INTERRUPTING -> INTERRUPTED

### 构造方式
> 通过传入Callable来构造一个任务  

```java
public FutureTask(Callable<V> callable) {
    if (callable == null)
        throw new NullPointerException();
    this.callable = callable;
    this.state = NEW;       // ensure visibility of callable
}
```

> 通过传入Runnable来构造一个任务

```java
public FutureTask(Runnable runnable, V result) {
    this.callable = Executors.callable(runnable, result);  // 将Runnable转换成Callable对象
    this.state = NEW;       // ensure visibility of callable
}
```

### 方法
在使用这个类是需要注意的是： 因为是一个异步执行任务，所以我们要理解这个异步的意义，异步也就意味着任务的执行和结果的获得是会在不同的线程中操作的，可能一个线程在执行任务，而有一个或者多个线程会等待着获取结果。
#### 任务的执行部分
> 执行一个任务-run()方法, 会被线程自动调用执行

```java
public void run() {
    // 判断任务的状态是否是初始化状态
    // 判断执行任务的线程对象runner是否为空,为空就将当前执行线程赋值给runner属性
    // 不为空说明已经有线程准备执行这个任务了
    if (state != NEW ||
        !UNSAFE.compareAndSwapObject(this, runnerOffset,
                                     null, Thread.currentThread()))
        return;
    try {
        Callable<V> c = callable;
        // 任务状态是NEW, 并且callable不为空(在任务被cancel()时,callable会被置空)则执行任务
        if (c != null && state == NEW) {
            V result;  // 临时存储任务结果
            boolean ran;  // 任务是否执行完毕
            try {
                result = c.call();  // 执行任务,返回结果
                ran = true;  
            } catch (Throwable ex) { // 任务执行异常
                result = null;  
                ran = false;
                setException(ex);
            }
            if (ran)  // 任务执行完毕就设置结果
                set(result);
        }
    } finally {
        // runner must be non-null until state is settled to
        // prevent concurrent calls to run()
        runner = null;  // 将执行任务的执行线程置空
        // state must be re-read after nulling runner to prevent
        // leaked interrupts
        int s = state; // 判断线程状态
        if (s >= INTERRUPTING)
            handlePossibleCancellationInterrupt(s);
    }
}

/**
* 执行结果的赋值操作, 子类可重写
**/
protected void set(V v) {
    // 首先将任务的状态改变
    // 状态改变成功之后再将结果赋值
    // 赋值成功,改变任务的状态
    // 处理等待线程队列(将线程阻塞状态改为唤醒,这样哪些等待获取结果的线程就可以取得任务结果)
    if (UNSAFE.compareAndSwapInt(this, stateOffset, NEW, COMPLETING)) {
        outcome = v;
        UNSAFE.putOrderedInt(this, stateOffset, NORMAL); // final state
        finishCompletion();
    }
}

/**
* 任务执行出现异常的处理方式, 子类可重写
**/
protected void setException(Throwable t) {
    if (UNSAFE.compareAndSwapInt(this, stateOffset, NEW, COMPLETING)) {
        outcome = t;
        UNSAFE.putOrderedInt(this, stateOffset, EXCEPTIONAL); // final state
        finishCompletion();
    }
}

/**
* 在任务执行完成(包括取消、正常结束、发生异常), 将等待线程列表唤醒
* 同时让任务执行体置空
**/
private void finishCompletion() {
    // assert state > COMPLETING;
    for (WaitNode q; (q = waiters) != null;) {
        if (UNSAFE.compareAndSwapObject(this, waitersOffset, q, null)) {
            for (;;) {
                Thread t = q.thread;
                if (t != null) {
                    q.thread = null;
                    LockSupport.unpark(t);
                }
                WaitNode next = q.next;
                if (next == null)
                    break;
                q.next = null; // unlink to help gc
                q = next;
            }
            break;
        }
    }

    done();  // 这里可以自定义实现任务完成后要做的事(在子类中重写done()方法)

    callable = null;        // to reduce footprint
}
```

> 任务可被多次执行- runAndReset()方法

```java
/**
* 可被子类重写
* 这个方法同run()方法的区别在于这个方法不会设置任务的执行结果值,而只是执行任务完之后,将任务的状态重新设置为初始化的可被执行状态.
**/
protected boolean runAndReset() {
    if (state != NEW ||
        !UNSAFE.compareAndSwapObject(this, runnerOffset,
                                     null, Thread.currentThread()))
        return false;
    boolean ran = false;
    int s = state;
    try {
        Callable<V> c = callable;
        if (c != null && s == NEW) {
            try {
                c.call(); // don't set result
                ran = true;
            } catch (Throwable ex) {
                setException(ex);
            }
        }
    } finally {
        // runner must be non-null until state is settled to
        // prevent concurrent calls to run()
        runner = null;
        // state must be re-read after nulling runner to prevent
        // leaked interrupts
        s = state;
        if (s >= INTERRUPTING)
            handlePossibleCancellationInterrupt(s);
    }
    return ran && s == NEW;
}
```

#### 任务的取消
> 只能取消还没被执行的任务(任务状态为NEW的任务)- cancel(boolean)

```java
public boolean cancel(boolean mayInterruptIfRunning) {
    // 如果任务状态不是初始化状态,则取消任务
    // (此时可能任务已经在执行了,并且可能已经执行完成,
    // 但是状态改变还没来得急改, 也就是在run()方法中的set()方法还没来得急调用)
    if (state != NEW)
        return false;
    if (mayInterruptIfRunning) {
        // 继续判断任务的当前状态是否为NEW,因为此时执行任务线程可能再度获得处理了,任务状态可能已发生改变
        if (!UNSAFE.compareAndSwapInt(this, stateOffset, NEW, INTERRUPTING))
            return false;
        // 如果任务状态依然是NEW, 也就是执行线程没有改变任务的状态, 
        // 则让执行线程中断(在这个过程中执行线程可能会改变任务的状态)
        Thread t = runner;
        if (t != null)
            t.interrupt();
        // 将任务状态改变为中断
        UNSAFE.putOrderedInt(this, stateOffset, INTERRUPTED); // final state
    }
    else if (!UNSAFE.compareAndSwapInt(this, stateOffset, NEW, CANCELLED))
        return false;
    finishCompletion();  // 处理任务完成的结果
    return true;
}
```

#### 获取任务结果
> 获取任务的执行结果-get()方法和get(long,TimeUnit)方法, 核心在于内部调用的awaitDone()方法

```java
public V get() throws InterruptedException, ExecutionException {
    int s = state;
    if (s <= COMPLETING)
        s = awaitDone(false, 0L);
    return report(s);
}

public V get(long timeout, TimeUnit unit)
    throws InterruptedException, ExecutionException, TimeoutException {
    if (unit == null)
        throw new NullPointerException();
    int s = state;
    if (s <= COMPLETING &&
        (s = awaitDone(true, unit.toNanos(timeout))) <= COMPLETING)
        throw new TimeoutException();
    return report(s);
}

private V report(int s) throws ExecutionException {
    Object x = outcome;
    if (s == NORMAL)  // 如果任务正常执行完,返回结果
        return (V)x;
    if (s >= CANCELLED)  // 抛出取消异常
        throw new CancellationException();
    throw new ExecutionException((Throwable)x);  // 其他情况抛出执行异常
}

/**
* 等待任务的执行结果
* timed: 是否有时间限制  nanos: 限制的时间
**/ 
private int awaitDone(boolean timed, long nanos)
    throws InterruptedException {
    // 计算限制的时间范围
    final long deadline = timed ? System.nanoTime() + nanos : 0L;
    // 当前等待线程节点
    WaitNode q = null;
    // 是否将节点放在了等待列表中
    boolean queued = false;
    // 无限循环来实现线程阻塞等待
    for (;;) {
        // 判断当前线程是否被中断
        if (Thread.interrupted()) {
            removeWaiter(q);
            throw new InterruptedException();
        }
        
        int s = state;
        /**
        * 1. 首先判断任务状态是否是完成状态, 是就直接返回结果
        * 2. 如果1为false,并且任务的状态是COMPLETING, 也就是在set()任务结果时被阻塞了,则让出当前线程cpu资源
        * 3. 如果前两步false,并且q==null,则初始化一个当前线程的等待节点
        * 4. 下一次循环体, 如果前3步依然是false,并且当前节点没有加入到等待列表,
        *     则将当前线程节点放在等待列表的第一个位置
        * 5. 在下一次循环, 如果前4步为false, 如果是时间范围内等待的,则判断当前时间是否过期,
        *    过期则将线程节点移出等待队列并返回任务状态结果, 如果没过期,则让当前线程阻塞一定时间
        * 6. 如果不是时间范围内等待, 并且前5步均为false,则让线程阻塞,直到被唤醒
        **/
        if (s > COMPLETING) {
            if (q != null)
                q.thread = null;
            return s;
        }
        else if (s == COMPLETING) // cannot time out yet
            Thread.yield();
        else if (q == null)
            q = new WaitNode();
        else if (!queued)
            queued = UNSAFE.compareAndSwapObject(this, waitersOffset,
                                                 q.next = waiters, q);
        else if (timed) {
            nanos = deadline - System.nanoTime();
            if (nanos <= 0L) {
                removeWaiter(q);
                return state;
            }
            LockSupport.parkNanos(this, nanos);
        }
        else
            LockSupport.park(this);
    }
}

/**
* 将线程节点从等待队列中移出
**/
private void removeWaiter(WaitNode node) {
    if (node != null) {
        node.thread = null;
        retry:
        for (;;) {          // restart on removeWaiter race
            for (WaitNode pred = null, q = waiters, s; q != null; q = s) {
                s = q.next;
                if (q.thread != null)
                    pred = q;
                else if (pred != null) {
                    pred.next = s;
                    if (pred.thread == null) // check for race
                        continue retry;
                }
                else if (!UNSAFE.compareAndSwapObject(this, waitersOffset,
                                                      q, s))
                    continue retry;
            }
            break;
        }
    }
}
```
### 总结
异步任务队列是线程安全的，在多线程下也只会被执行一次任务。需要注意的是在多个状态之间切换的一些细微处。同时也要了解在实现中对==sun.misc.Unsafe==类的使用。