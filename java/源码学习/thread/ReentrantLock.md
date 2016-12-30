## java源码解析-ReentrantLock类

ReentrantLock是java1.5中lock框架中Lock接口的一个实现类。一个ReentrantLock的实例锁拥有和synchronized相同的行为和语义,并且还扩展了其他能力。  

ReentrantLock是通过一种什么机制来实现锁的呢？也就是说ReentrantLock到底是锁住了什么东西来控制线程同步的呢？下面将详细讲解.。 
ReentrantLock内部实现了一个AbstractQueuedSynchronizer的同步器，ReentrantLock就是通过控制这个AbstractQueuedSynchronizer同步器来实现多个线程之间的同步。那么具体的实现原理是怎么样的呢？ 
AbstractQueuedSynchronizer类会维护一个等待队列，并且拥有一个state(volatile修饰)的成员变量属性，AbstractQueuedSynchronizer就是通过这个state来控制多个线程的同步访问的。当ReentrantLock调用lock()方法时，内部实际调用的是Sync(AbstractQueuedSynchronizer的实现类)的lock方法，此方法会先判断AbstractQueuedSynchronizer中state属性的值：如果state!=0，说明当前有线程在占用锁资源，然后接着判断当前占用该锁资源的线程是否跟当前线程是同一个线程，如果是同一个线程那么state值就加一，同时当前线程继续执行，如果不是同一个线程，那么就会把当前线程加入等待队列(并且会一直尝试获取锁资源)。如果state\==0，说明当前锁资源是可用，那么直接设置当前线程占用了锁资源并返回继续执行。执行完成之后，==必须要手动释放锁资源==。

下面对ReentrantLock的源码并相关类源码进行解读：
### 类定义
```java
public class ReentrantLock implements Lock, java.io.Serializable
```
### 成员变量
| 修饰符                | 变量名  | 作用        |
| ------------------ | ---- | --------- |
| private final Sync | sync | 实际同步锁控制实例 |

---

### 构造方法
> **无参构造方法**  
> 无参构造方法会给sync赋值,默认赋值为不公平锁同步(公平锁,不公平锁下面会讲)
```java
public ReentrantLock() {
    sync = new NonfairSync();
}
```

> **有参构造方法**  
> 参数：
> true 公平锁，线程获取锁的顺序跟等待的时间有关，先等待的线程先获取锁  
> false 不公平锁，线程获取锁是随机的  

```java
public ReentrantLock(boolean fair) {
    sync = fair ? new FairSync() : new NonfairSync();
}
```

---

### 核心方法讲解
> **lock()方法**  

```java
public void lock() {
    // 调用Sync(Sync是AbstractQueuedSynchronizer的子类)的lock()方法
    // Sync是ReentrantLock的内部类,同时在ReentrantLock中有两种实现方式
    // 1. FairSync(公平实现)
    // 2. NonFairSync(不公平实现)
    sync.lock();
}

-- ------------------------------------------------------ --
-- ---------------FairSync获取锁的实现-------------------- --
-- ------------------------------------------------------ --
/**
* FairSync的lock方法实现
**/
final void lock() {
    // acquire方法是AbstractQueuedSynchronizer中的方法
    // 此方法会调用tryAcquire(int)方法来试图获取锁资源
    acquire(1);
}

/**
* AbstractQueuedSynchronizer中acquire()方法的实现
**/
public final void acquire(int arg) {
    // 试图获取锁资源(会调用对应实现的tryAcquire()方法),
    // 不成功则加入等待队列并获取队列(addWaiter()会将当前线程加入到等待队列尾部)
    // acquireQueued()(不知道怎么说,等会直接讲代码)
    if (!tryAcquire(arg) &&
        acquireQueued(addWaiter(Node.EXCLUSIVE), arg))
        selfInterrupt();
}

/**
* FairSync中tryAcquire()的实现
* 返回
*   true: 获取锁成功
*   false: 获取锁不成功
**/
protected final boolean tryAcquire(int acquires) {
    // 获取当前线程
    final Thread current = Thread.currentThread();
    // 获取锁资源的状态
    // 0: 说明当前锁可立即获取,在此种状态下(又是公平锁)
    // >0并且当前线程与持有锁资源的线程是同一个线程则state + 1并返回true
    // >0并且占有锁资源的不是当前线程,则返回false表示获取不成功
    int c = getState();
    if (c == 0) {
        // 在锁可以立即获取的情况下
        // 首先判断线程是否是刚刚释放锁资源的头节点的下一个节点(线程的等待先后顺序)
        // 如果是等待时间最长的才会马上获取到锁资源,否则不会(这也是公平与不公平的主要区别所在)
        if (!hasQueuedPredecessors() &&
            compareAndSetState(0, acquires)) {
            setExclusiveOwnerThread(current);
            return true;
        }
    }
    else if (current == getExclusiveOwnerThread()) {  //线程可以递归获取锁
        int nextc = c + acquires;
        // 超过int上限值抛出错误
        if (nextc < 0)
            throw new Error("Maximum lock count exceeded");
        setState(nextc);
        return true;
    }
    return false;
}

/**
* AbstractQueuedSynchronizer中acquireQueued()方法讲解
**/
final boolean acquireQueued(final Node node, int arg) {
    boolean failed = true;
    try {
        boolean interrupted = false;
        // 注意这里的无限循环(表现出来就是等待的一个过程)
        for (;;) {
            // 获得前一个非null节点
            final Node p = node.predecessor();
            // 如果前任节点是头结点(头节点是表示获取到了资源锁的节点)
            // 并且试图获取锁资源(只有在p节点release()掉锁资源才会获取成功)
            // 如果获取锁资源成功,就将此线程节点设置为头结点,并把p节点的引用断开
            // 获取锁成功就退出循环
            if (p == head && tryAcquire(arg)) {
                setHead(node);
                p.next = null; // help GC
                failed = false;
                return interrupted;
            }
            
            // 如果获取资源失败了,那就先判断线程是否需要被阻塞,需要就阻塞线程
            if (shouldParkAfterFailedAcquire(p, node) &&
                parkAndCheckInterrupt())
                interrupted = true;
                
            // 继续循环等待
        }
    } finally {
        // 如果获取锁资源失败了,
        // 那么就把线程的等待状态设置为cancelled(此状态的线程不会再获得锁资源)
        if (failed)
            cancelAcquire(node);
    }
}


-- ------------------------------------------------------ --
-- ---------------NonFairSync获取锁的实现----------------- --
-- ------------------------------------------------------ --
/**
* lock()方法实现
**/
final void lock() {
    // 如果锁空闲则立即获取锁资源,设置锁资源的当前占用线程
    if (compareAndSetState(0, 1))
        setExclusiveOwnerThread(Thread.currentThread());
    else
        // acquire()过程跟FairSync是一样的,
        // 主要区别在于NonFairSync的tryAcqure()有不同的实现
        acquire(1);
}

/**
* tryAcquire()方法实现
* 调用的nonfairTryAcquire()方法,这是父类Sync中的方法实现
**/
protected final boolean tryAcquire(int acquires) {
    return nonfairTryAcquire(acquires);
}

/**
* Sync中的nonfairTryAcquire()方法实现
* 这个跟公平类中的实现主要区别在于不会判断当前线程是否是等待时间最长的线程
**/ 
final boolean nonfairTryAcquire(int acquires) {
    final Thread current = Thread.currentThread();
    int c = getState();
    if (c == 0) {
        // 跟FairSync中的主要区别,不会判断hasQueuedPredecessors()
        if (compareAndSetState(0, acquires)) {
            setExclusiveOwnerThread(current);
            return true;
        }
    }
    else if (current == getExclusiveOwnerThread()) {
        int nextc = c + acquires;
        if (nextc < 0) // overflow
            throw new Error("Maximum lock count exceeded");
        setState(nextc);
        return true;
    }
    return false;
}
```

> **unlock()方法**  
> 释放线程占用的锁

```java
public void unlock() {
    // 调用Sync中的release()方法,所以公平和不公平类释放锁是一样的
    // 实际调用AbstractQueuedSynchronizer中的release()
    sync.release(1);
}

/**
* AbstractQueuedSynchronizer中的release()
**/
public final boolean release(int arg) {
    // 释放锁资源,子类会重写这个方法(在这里就是调用的Sync中tryRelease())
    if (tryRelease(arg)) {
        // 释放锁资源成功后,会执行一个唤醒后续线程(唤醒一个)的操作
        Node h = head;
        if (h != null && h.waitStatus != 0)
            unparkSuccessor(h);
        return true;
    }
    return false;
}

/**
* Sync中tryRelease()
**/
protected final boolean tryRelease(int releases) {
    // 修改当前锁的状态
    // 如果一个线程递归获取了该锁(也就是state != 1), 那么c可能不等0
    // 如果没有线程递归获取该锁,则c == 0
    int c = getState() - releases;
    
    // 如果锁的占有线程不等于当前正在执行释放操作的线程,则抛出异常
    if (Thread.currentThread() != getExclusiveOwnerThread())
        throw new IllegalMonitorStateException();
    boolean free = false;
    // c == 0,表示当前线程释放锁成功,同时表示递归获取了该锁的线程已经执行完毕
    // 则设置当前锁状态为free,同时设置锁的当前线程为null,可以让其他线程来获取
    // 同时也说明,如果c != 0,则表示线程递归占用了锁资源,
    // 所以锁的当前占用线程依然是当前释放锁的线程(实际没有释放)
    if (c == 0) {
        free = true;
        setExclusiveOwnerThread(null);
    }
    // 重新设置锁的占有数
    setState(c);
    return free;
}
```
> **条件锁的操作**  

```java
public Condition newCondition() {
    return sync.newCondition();
}

这里不对源码进行解读,只对原理进行一个说明,上述函数实际上是执行了一个new ConditionObject();的操作,这个类是同步器中的一个内部类,这个类内部是维护了一个条件队列,条件锁的操作也是针对这个条件队列的一个操作.
```
