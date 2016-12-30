## java源码解析-ThreadLocal类

ThreadLocal类会维护一些线程的本地副本或者说是变量(严格来说并不是ThreadLocal维护的, 它只是起了一个中间作用)，这些变量(存放在ThreadLocalMap中--是一个Map,键是ThreadLocal)只能够在线程生命周期内被访问，而且也只能是在同一个线程内访问，也就是如果线程A设置了一个变量值，那么就只有线程A能访问到这个变量值，其他的线程访问不到。在线程终止之后,相应的这些本地变量也会成为垃圾收集器的对象。

jdk推荐使用ThreadLocal类的方式是：作为类的私有静态成员变量 

```java
/*<tt>ThreadLocal</tt> instances are typically private
 * static fields in classes that wish to associate state with a thread (e.g.,
 * a user ID or Transaction ID).*/
```

下面我们来看看具体是怎么实现的：

首先从Thread类中，我们知道Thread维护了一个实例变量ThreadLocal.ThreadLocalMap threadLocals； (注意这里维护的实例变量是ThreadLocal中的ThreadLocalMap类型, 而并不是ThreadLocal类型)，从这个变量线程就可以访问到本地维护的变量。

### 核心方法

> initialValue()-设置初始值，在调用get()方法时，如果当前线程的threadLocals变量为null,那么get()将会给线程的threadLocals赋值，同时也会给他设置该方法的返回值，并作为get()方法的返回值；在new ThreadLocal()时，应该要重写这个方法，返回自己想要的值。

```java
protected T initialValue() {
	return null;
}
```

> set(T value)-给线程设置本地变量，value是值，键是调用这个方法的ThreadLocal对象

```java
public void set(T value) {
        Thread t = Thread.currentThread();     //获取当前执行线程
        ThreadLocalMap map = getMap(t);    //获取线程中的实例变量threadLocals
        if (map != null)       //  如果线程中的实例变量threadLocals已经创建, 就直接设值, 键是this--也就是调用该方法的ThreadLocal实例
            map.set(this, value);
        else                         //  如果线程中的实例变量threadLocals还没有被创建, 则用t, value创建一个
            createMap(t, value);
    }
    //那下面我们就看一下getMap(t)和createMap()方法的实现:
    //getMap(thread):  返回当前线程的threadLocals, 要注意的是threadLocals是ThreadLocalMap的实例,并不是ThreadLocal的实例, 千万不要被这个名字给忽悠了,我就是被这个名字忽悠了,搞得第一次没看懂
    ThreadLocalMap getMap(Thread t) {
        return t.threadLocals;
    }
    //createMap():  给当前线程的threadLocals变量赋值
    void createMap(Thread t, T firstValue) {
        t.threadLocals = new ThreadLocalMap(this, firstValue);  //  创建新的ThreadLocalMap实例, 这是在一个线程中第一次调用(所有ThreadLocal实例的总的第一次调用)ThreadLocal的get()时访问
    }
```

> get()-获取通过set()方法设置的value值

```java
 public T get() {
        Thread t = Thread.currentThread();
        ThreadLocalMap map = getMap(t);
        if (map != null) {
            ThreadLocalMap.Entry e = map.getEntry(this);      //  获取当前threadLocal实例对应的Entry
            if (e != null)
                return (T)e.value;
        }
        return setInitialValue();     //  如果map为空(说明线程中的threadLocals还没被创建,或者是还没设置值),则调用这个方法
    }
    //下面看看setInitialValue()做了些什么:
    private T setInitialValue() {
        T value = initialValue();   //  取得初始值
        Thread t = Thread.currentThread();   // 获取线程
        ThreadLocalMap map = getMap(t);  // 获取线程threadLocals
        if (map != null)   
            map.set(this, value);    //  设置值
        else
            createMap(t, value);   //  创建threadLocals
        return value;   //返回初始值
    }
```

下面我们来看一下用于存储线程本地变量值的ThreadLocalMap类：它的实现主要是通过维护一个Entry[]数组来实现的，Entry跟HashMap中的Entry类似，也是一个键值对存储，键是ThreadLocal实例。

```java
static class Entry extends WeakReference<ThreadLocal> {
  /** The value associated with this ThreadLocal. */
  Object value;

  Entry(ThreadLocal k, Object v) {
    super(k);
    value = v;
  }
}
```

ThreadLocalMap类的成员变量：

private static final int INITIAL_CAPACITY = 16; // 初始容量

private Entry[] table;  //存储值的数组对象, 在构造函数中初始化, 事先我一直没想明白为什么要用一个数组来存储,因为在我当时的思维里,总是想着只有一个threadLocal实例时的情况, 如果只有一个threadLocal实例,完全是没必要弄一个数组来存储的,后来想通了, 就是如果有多个ThreadLocal类的实例,并且在线程执行期间,这多个实例多调用了set()方法,那么这个数组就可以理解,从而我们可以知道table的真是大小是跟线程执行过程中多个ThreadLocal实例调用set()方法的次数(如果同一个实例多次调用set()方法只会加1,因为会替换)

private int size = 0;  // table中存放的实际数

private int threshold;  // 扩容标识

构造方法:

ThreadLocalMap(t, value): 用ThreadLocal实例和value值构建一个ThreadLocalMap

ThreadLocalMap(ThreadLocal firstKey, Object firstValue) {

            table = new Entry[INITIAL_CAPACITY];   // 初始化

            int i = firstKey.threadLocalHashCode & (INITIAL_CAPACITY - 1);   // 下标索引

            table[i] = new Entry(firstKey, firstValue);   // 创建Entry

            size = 1;

            setThreshold(INITIAL_CAPACITY);

        }

ThreadLocalMap(ThreadLocalMap parentMap): 用一个ThreadLocalMap来构建一个ThreadLocalMap

方法:

set(ThreadLocal key, Object value):  设置插入键值对, 

Entry getEntry(ThreadLocal key):  获取Entry, 根据ThreadLocal计算出索引,将索引处的Entry返回

private Entry getEntry(ThreadLocal key) {

            int i = key.threadLocalHashCode & (table.length - 1);

            Entry e = table[i];

            if (e != null && e.get() == key)

                return e;

            else

                return getEntryAfterMiss(key, i, e);     //  根据索引值没找到时的处理策略, 这是在插入值时发生hash碰撞会发生的情况

}

[参考](http://qifuguang.me/2015/09/02/[Java%E5%B9%B6%E5%8F%91%E5%8C%85%E5%AD%A6%E4%B9%A0%E4%B8%83]%E8%A7%A3%E5%AF%86ThreadLocal/)

