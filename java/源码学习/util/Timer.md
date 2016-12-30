## java源码解析-Timer类

Timer是jdk1.3中自带的定时任务框架系统。一个调度定时任务的工具线程类。可以执行一个只调度一次的任务也可以重复调度一个一定间隔时间的任务。  
一个Timer实例就是一个调度任务调度线程。当任务队列中的所有定时任务被执行完毕，这个定时调度的线程就会自动终止。如果你想让这个线程快速终止的话， 那么你可以直接调用cancel()方法可以让调度线程快速终止。  
Timer类是线程安全类：多个线程可以共享一个Timer实例。同时这个类不保证准时调度任务，因为他是用的Object.wait(long)方法。  
Timer类能够支持大量的并行调度任务(成百上千没问题)，在Timer内部存储每个调度任务的结构是以==平衡二叉树堆([堆排序](http://baike。baidu。com/view/157305。htm))==的结构来保存每个任务对象的，这种存储结构能在log(n)的时间复杂度内快速的查询。  
jdk1.5中提供了比Timer跟高效的定时调度线程池类ScheduledThreadPoolExecutor  

---
### 类定义

```
public class Timer {}
```
### 成员变量
| 修饰符                                | 变量名                                     | 作用            |
| ---------------------------------- | --------------------------------------- | ------------- |
| private final TaskQueue            | queue = new TaskQueue()                 | 任务队列          |
| private final TimerThread          | thread = new TimerThread(queue)         | 定时调度任务的线程类    |
| private final Object               | threadReaper                            | 用于终止定时调度线程的   |
| private final static AtomicInteger | nextSerialNumber = new AtomicInteger(0) | 用于生成定时调度线程的名字 |

### 构造方法  
当我们实例化一个Timer类之后， 定时调度的线程就会启动start()一直等待(queue.wait())任务队列中加入任务
```java
public Timer() {
    this("Timer-" + serialNumber());
}

public Timer(boolean isDaemon) {
    this("Timer-" + serialNumber(), isDaemon);
}

public Timer(String name) {
    thread.setName(name);
    thread.start();
}

public Timer(String name, boolean isDaemon) {
    thread.setName(name);
    thread.setDaemon(isDaemon);
    thread.start();
}
```

### 核心方法
Timer中有多个重载的public void schedule()方法，但是这些重载的方法只会做一些参数的判断，并都最终调度的sched()方法，所以下面将讲解sched()方法。

> **sched()方法**  

```java
private void sched(TimerTask task, long time, long period) {
    if (time < 0)
        throw new IllegalArgumentException("Illegal execution time.");

    // Constrain value of period sufficiently to prevent numeric
    // overflow while still being effectively infinitely large.
    if (Math.abs(period) > (Long.MAX_VALUE >> 1))
        period >>= 1;
    
    // 获取任务队列的锁(同一个线程多次获取这个锁并不会被阻塞，不同线程获取时才可能被阻塞)
    synchronized(queue) {
        // 如果定时调度线程已经终止了，则抛出异常结束
        if (!thread.newTasksMayBeScheduled)
            throw new IllegalStateException("Timer already cancelled.");
        
        // 再获取定时任务对象的锁(为什么还要再加这个锁呢?想不清)
        synchronized(task.lock) {
            // 判断线程的状态，防止多线程同时调度到一个任务时多次被加入任务队列
            if (task.state != TimerTask.VIRGIN)
                throw new IllegalStateException(
                    "Task already scheduled or cancelled");
                    
            // 初始化定时任务的下次执行时间
            task.nextExecutionTime = time;  
            // 重复执行的间隔时间
            task.period = period;  
            // 将定时任务的状态由TimerTask.VIRGIN(一个定时任务的初始化状态)设置为TimerTask.SCHEDULED
            task.state = TimerTask.SCHEDULED;  
        }
        
        // 将任务加入任务队列
        queue.add(task);
        // 如果当前加入的任务是需要第一个被执行的(也就是他的下一次执行时间离现在最近)
        // 则唤醒等待queue的线程(对应到上面提到的queue.wait())
        if (queue.getMin() == task)
            queue.notify();
    }
}
```

> **cancel()终止定时线程**  

```java
public void cancel() {
    // 从这里可以知道，如果调用了Timer的cancel()方法也不会立刻就终止定时调度线程
    // 因为这里需要获取任务队列的锁，如果TimerThread占用了queue的锁，也就是说queue并没有在wait()，
    // 那么cancel就不会立刻终止定时线程， 他需要等待TimerThread定时线程释放掉queue的锁
    // 也就是说如果queue队列中有定时任务存在，那么cancel就不会终止定时线程，他需要等到queue中的定时任务被清空
    // 用一句话说: cancel会等到所有定时任务执行完后立刻终止定时线程
    synchronized(queue) {
        thread.newTasksMayBeScheduled = false;
        queue.clear();
        queue.notify();  // In case queue was already empty.
    }
}
```

> **purge()方法**  
> 从队列中移除所有状态为cancelled的任务，调用这个方法并不会影响timer的行为，但是一般应用不会调用这个方法，除非有很多被cancelled的任务；同时调用这个方法也会比较消耗时间。

---
## TimerThread类
TimerThread是Timer中定时调度线程类的定义，这个类会做为一个线程一直运行来执行Timer中任务队列中的任务。
### 类定义
```java
class TimerThread extends Thread
```
### 成员变量
| 修饰符               | 变量名                           | 作用                                       |
| ----------------- | ----------------------------- | ---------------------------------------- |
| boolean           | newTasksMayBeScheduled = true | 用于控制当queue任务队列为空时，定时线程是否应该立刻终止(false立刻终止) |
| private TaskQueue | queue                         | 任务队列(这个当TimerThread在Timer中被实例化时会传入)      |

### 方法
> **run()方法**  
> 定时线程的执行方法，它会调用TimerThread类的mainLoop()方法。

```java
public void run() {
    try {
        mainLoop();
    } finally {
        // Someone killed this Thread， behave as if Timer cancelled
        synchronized(queue) {
            newTasksMayBeScheduled = false;
            queue.clear();  // Eliminate obsolete references
        }
    }
}
```

> **mainLoop()方法**  

```java
private void mainLoop() {
    // 无限循环来控制等待任务队列中加入任务
    while (true) {
        try {
            TimerTask task;
            boolean taskFired;
            // 获取任务队列的锁
            synchronized(queue) {
                // 如果任务队列为空，并且线程没有被cancel()
                // 则线程等待queue锁，queue.wait()方法会释放获得的queue锁
                // 这样在Timer中sched()方法才能够获取到queue锁
                while (queue.isEmpty() && newTasksMayBeScheduled)
                    queue.wait();
                
                // 如果任务队列为空了，那么就退出循环
                // 这种情况要发生，那么必须newTasksMayBeScheduled=false
                // 因为如果newTasksMayBeScheduled=true，就会在上面的while循环中执行queue.wait()，使线程进入等待状态
                // 等线程从等待状态恢复时，说明queue.notify()方法被调用了，
                // 而观察Timer代码这只可能在sched()方法中发生， 这个方法会在队列queue中add任务而使queue不再为空
                if (queue.isEmpty())
                    break; 

                long currentTime, executionTime;
                // 得到任务队列中的位置1的任务
                task = queue.getMin();
                // 获取任务的锁
                synchronized(task.lock) {
                    // 如果任务被取消了(TimerTask.cancel()方法被调用)
                    // 将任务从队列中移除，继续重新循环
                    if (task.state == TimerTask.CANCELLED) {
                        queue.removeMin();
                        continue;  // No action required， poll queue again
                    }
                    
                    // 获取任务的执行时间
                    currentTime = System.currentTimeMillis();
                    executionTime = task.nextExecutionTime;
                    
                    // 计算任务是否应该被触发
                    if (taskFired = (executionTime<=currentTime)) {
                        // 任务应该被触发，并且不是重复任务
                        // 将任务从队列中移除并修改任务的执行状态
                        if (task.period == 0) { // Non-repeating， remove
                            queue.removeMin();
                            task.state = TimerTask.EXECUTED;
                        } else { // 任务是重复执行任务，计算任务下一次应该被执行的时间，并重新排序任务队列
                            queue.rescheduleMin(
                              task.period<0 ? currentTime   - task.period
                                            : executionTime + task.period);
                        }
                    }
                }
                // 如果任务不应被触发，让其等待一定时间后执行
                if (!taskFired) // Task hasn't yet fired; wait
                    queue.wait(executionTime - currentTime);
            }
            // 任务应该被触发，让任务执行
            if (taskFired)  // Task fired; run it, holding no locks
                task.run();  // 任务也是一个线程
        } catch(InterruptedException e) {
        }
    }
}
```

---
## TaskQueue类
TaskQueue是一个任务队列类，用于保存定时器需要执行的定时任务，这个队列是一个数组，只不过是一种平衡二叉树堆结构的数组。至于这个树堆是怎么样一种结构，还请执行百度。只能说这种结构总是保证值最小或者是值最大的在数组中的第一个位置(这个类中始终是nextExecuteTime最小的在第一个位置)，没当队列有增加，删除操作就会重新调整队列结构，让nextExecuteTime值最小的放在第一个位置。

---
## TimerTask类
TimerTask任务类，继承自Thread，如果我们要用Timer来做定时任务，那么我们必须继承TimerTask类，并且实现run()方法(具体任务代码)。如果要取消一个任务的调度，则调用TimerTask.cancel()方法将取消任务的执行。