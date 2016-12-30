## java源码解析-ArrayList类

Java中ArrayList的实现原理：ArrayList内部会维护一个Object的数组对象，针对ArrayList的任何操作在内部都是通过操作Object这个数组对象的实现的，那么ArrayList是如何实现动态的扩容的呢？首先ArrayList中有一个成员变量**size**来记录ArrayList实际存储的数据量大小，当每次add新的数据时，就会通过这个size和Object数组的length比较，当$size + 1>length$就会扩容到原数组大小的1.5倍，然后再执行add操作。  
**效率：** ==根据索引查找快，末尾插入快，中间插入要执行后面位置数据复制，查找特定对象慢(一样要循环比较)==

### 成员变量讲解

| 修饰符                           | 变量名               | 作用                          |
| ----------------------------- | ----------------- | --------------------------- |
| private static final int      | DEFAULT_CAPACITY  | 默认的初始化数组存储空间大小              |
| private static final Object[] | EMPTY_ELEMENTDATA | 空的实例list                    |
| private transient Object[]    | elementData       | 存储数据的数组对象                   |
| private int                   | size              | list中存储数据的实际容量              |
| protected transient int       | modCount          | 继承自AbstractList,记录size变化的次数 |

### 构造方法
> 空的构造方法：创建一个空的list，不包含任何数据，list中包含的数组长度也是空的，
> 此种方式下，当add第一个元素时，才会用DEFAULT_CAPACITY默认容量来重新new一个Object[]

```java
public ArrayList() {
    super();
    this.elementData = EMPTY_ELEMENTDATA;
}
```
> 构造初始化数组容量的list：根据initialCapacity参数来实例化一个存储数据的数组

```java
public ArrayList(int initialCapacity) {
    super();
    if (initialCapacity < 0)
        throw new IllegalArgumentException("Illegal Capacity: "+
                                           initialCapacity);
    this.elementData = new Object[initialCapacity];
}
```
> 从其他Collection构造：

```java
public ArrayList(Collection<? extends E> c) {
    elementData = c.toArray();
    size = elementData.length;
    // c.toArray might (incorrectly) not return Object[] (see 6260652)
    if (elementData.getClass() != Object[].class)  // 类型转换
        elementData = Arrays.copyOf(elementData, size, Object[].class);
}
```
### 方法选讲
> **public int indexOf(Object o)**  
> 返回对象o在list中第一次出现的下标值

```java
public int indexOf(Object o) {
    if (o == null) {
        for (int i = 0; i < size; i++)
            if (elementData[i]==null)
                return i;
    } else {
        for (int i = 0; i < size; i++)
            if (o.equals(elementData[i]))
                return i;
    }
    return -1;
}
```
> **public int lastIndexOf(Object o)**  
> 返回对象o在list中最后一次出现的下标值

```java
public int lastIndexOf(Object o) {
    if (o == null) {
        for (int i = size-1; i >= 0; i--)
            if (elementData[i]==null)
                return i;
    } else {
        for (int i = size-1; i >= 0; i--)
            if (o.equals(elementData[i]))
                return i;
    }
    return -1;
}
```
> **public E get(int index)**  
> 获取下标index处的对象

```java
public E get(int index) {
    rangeCheck(index);  // 范围检查
    return elementData(index); // elementData[index]
}
```
> **public boolean add(E e)**  
> 在list末尾添加一个新的元素，同时size执行加1操作 此操作会先检查数组的容量(目的：如果当前数组所有位置都已存放了数据，直接添加就会报错，下标越界，所以要先检查容量是否已满)

```java
public boolean add(E e) {
    ensureCapacityInternal(size + 1);  // 扩充数组容量, 抛出OutOfMemoryError
    elementData[size++] = e; // 在末尾设置值
    return true;
}
```
> **public void add(int index, E element)**  
> 在指定下标位置添加元素，同时size+1操作，此方法跟add()方法一样

```java
public void add(int index, E element) {
    rangeCheckForAdd(index);

    ensureCapacityInternal(size + 1);  // Increments modCount!!
    System.arraycopy(elementData, index, elementData, index + 1,
                     size - index);
    elementData[index] = element;
    size++;
}
```

> **public boolean retainAll(Collection<?> c)**  
> 从当前list中移除在c中不存在的元素

> **ArrayList中的迭代器 Iterator 讲解**  
> 首先我们要清楚的一点就是迭代器的工作原理是什么？ list中的迭代器是根据list的修改次数来实现迭代的，什么意思呢？说白一点就是根据list的继承成员属性modCount来工作的。modCount-list的size发生改变modCount就会加一或者减一,这里要注意的是size的改变时针对的size的改变次数，而不是size的变化长度，也就是说如果size一次增加的数量不管是多少，modCount始终只会加一；比如add()一次增加一个元素modCount+1，addAll()一次增加多个元素modCount还是+1。这样造成的问题有：  
> a. 如果我们在用Iterator做循环操作时， 在循环操作中调用了add()， remove()等会改变size值的操作，都将引起异常抛出(ConcurrentModificationException)； 
> b. 在多线程的环境下，如果一个线程用Iterator在操作list，而另一个线程在操作同一个list，那么也会引起抛出异常(ConcurrentModificationException)。此种情况可以考虑==Vector==；  
> ==补充：在这种情况下使用Vector也是于事无补，虽然在Vector中针对Iterator的next()和remove()都添加了针对当前list的synchronized条件，但始终next()和add()这些并不是一个原子性的操作，所以在Vector中还是会有同样的问题存在。==


ArrayList中有两种Iterator  
1. Itr内部类-iterator()方法，可执行hasNext，next()，remove()操作，要remove()则必须先执行next();
2. ListItr内部类-listIterator()方法，多了操作list数据的方法add()，set()方法

---
ArrayList影响modCount的方法有：add()，remove()，clear()，addAll()；也就是这些方法都会影响Iterator迭代器的执行。