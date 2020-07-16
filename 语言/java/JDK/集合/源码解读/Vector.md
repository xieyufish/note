## java源码解析-Vector类

Vector是java中线程安全的list，每个公有方法均被synchronized修饰，在多线程环境下推荐使用这个类来创建list。与Arraylist在一些区别是：  
a. 容量会自动扩充和缩小  
b. 容量扩充的大小通过构造方式传入或者默认的两倍

### 类定义
```java
public class Vector<E>
    extends AbstractList<E>
    implements List<E>, RandomAccess, Cloneable, java.io.Serializable
```
### 成员变量
| 修饰符                     | 变量名               | 作用                                  |
| ----------------------- | ----------------- | ----------------------------------- |
| protected int           | capacityIncrement | 扩容大小                                |
| protected Object[]      | elementData       | 存储数据的数组对象                           |
| protected int           | elementCount      | list中存储数据的实际容量                      |
| protected transient int | modCount          | 继承自AbstractList,记录elementCount变化的次数 |

### 构造方式
> public Vector(int initialCapacity, int capacityIncrement)  
> 创建一个容量大小为initialCapacity，每次扩容增加capacityIncrement个位置的vector

> public Vector(int initialCapacity)  
> 创建一个容量大小为initialCapacity，每次扩容到当前容量的两倍

> public Vector()  
> 创建一个容量大小为10，每次扩容到当前容量的两倍

> public Vector(Collection<? extends E> c)  
> 从其他Collection创建vector

### 方法
> **public synchronized void setSize(int newSize)**  
> 设定当前vector的elementCount值，如果newSize>elementCount，那么多余的位置用null填充，如果newSize<elementCount，那么截断newSize后的元素。

```java
public synchronized void setSize(int newSize) {
    modCount++;
    if (newSize > elementCount) {
        ensureCapacityHelper(newSize);
    } else {
        for (int i = newSize ; i < elementCount ; i++) {
            elementData[i] = null;
        }
    }
    elementCount = newSize;
}
```

> **public Enumeration\<E> elements()**  
> 返回这个vector数据结构的枚举类型表示

```java
public Enumeration<E> elements() {
    return new Enumeration<E>() {
        int count = 0;

        public boolean hasMoreElements() {
            return count < elementCount;
        }

        public E nextElement() {
            // 这个方法的调用仍然会有多线程的问题, 多个线程遍历时,可能不会遍历到所有
            synchronized (Vector.this) {
                if (count < elementCount) {
                    return elementData(count++);
                }
            }
            throw new NoSuchElementException("Vector Enumeration");
        }
    };
}
```
> **==public synchronized int indexOf(Object o, int index)==**  
> 从索引index向后搜索o对象的索引位置，ArrayList中无此方法  

> **==public synchronized int lastIndexOf(Object o, int index)==**  
> 从索引index向前搜索o的索引位置

> **public synchronized E elementAt(int index)**  
> 索引index处的对象，对应ArrayList中的get()方法  