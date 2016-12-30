## java源码解析-LinkedHashMap类

LinkedHashMap是继承自HashMap，并实现Map接口。所以他拥有HashMap的结构，同时他又有LinkedList的结构(双向链表的结构-环形的)。由于LinkedHashMap中维护了一个双向链表结构，所以在LinkedHashMap中的某些遍历操作是直接针对的双向链表的(例如：contains操作,Iterator操作)。

下面是LinkedHashMap中entry结构:
```java
// 继承自HashMap中的entry,并增加了两个额外的属性
private static class Entry<K,V> extends HashMap.Entry<K,V> {
    // These fields comprise the doubly linked list used for iteration.
    Entry<K,V> before, after;

    Entry(int hash, K key, V value, HashMap.Entry<K,V> next) {
        super(hash, key, value, next);
    }
}
```
### 类定义
```java
public class LinkedHashMap<K,V>
    extends HashMap<K,V>
    implements Map<K,V>
```
### 成员变量(拥有HashMap中所有的非私有成员变量)
| 修饰符                           | 变量名         | 作用                                    |
| ----------------------------- | ----------- | ------------------------------------- |
| private transient Entry\<K,V> | header      | 链表结构的头结点(hash值取的-1)                   |
| private final boolean         | accessOrder | 是否让链表随机访问(true随机get方法的影响,false就是插入顺序) |

### 方法
> **初始化方法init()**  
> 在构造函数中被调用，会初始化链表的头结点header

```java
@Override
void init() {
    header = new Entry<>(-1, null, null, null);
    header.before = header.after = header;
}
```

> **添加元素put()**  
> 继承自HashMap方法，且没有重写，所以它的数组结构跟HashMap是一样的：只是它重写了addEntry()和createEntry()方法(这两个方法会在put的时候被调用到)，这两个方法会增多一个双向链表的结构。下面讲解：

```java
void addEntry(int hash, K key, V value, int bucketIndex) {
    // 调用父类HashMap中的addEntry()方法(会调用createEntry()方法)
    super.addEntry(hash, key, value, bucketIndex); 

    // Remove eldest entry if instructed
    Entry<K,V> eldest = header.after;
    // 移除老的Entry(不会执行,除非子类重写removeEldestEntry方法)
    if (removeEldestEntry(eldest)) {
        removeEntryForKey(eldest.key);
    }
}

void createEntry(int hash, K key, V value, int bucketIndex) {
    HashMap.Entry<K,V> old = table[bucketIndex];
    Entry<K,V> e = new Entry<>(hash, key, value, old);
    table[bucketIndex] = e;
    
    // 双向链表的操作,在header节点前面链接e节点
    // 所以每次添加的新的entry,在双向链表中的位置始终是在header前面
    e.addBefore(header);
    size++;
}

/**
* Entry类中的addBefore()方法
* 在existingEntry节点前增加当前调用方法的实例节点
**/
private void addBefore(Entry<K,V> existingEntry) {
    after  = existingEntry;
    before = existingEntry.before;
    before.after = this;
    after.before = this;
}
```

> **获取元素get()**  
> 跟HashMap是一样的，直接调用的父类HashMap中的getEntry()方法。

```java
public V get(Object key) {
    Entry<K,V> e = (Entry<K,V>)getEntry(key);
    if (e == null)
        return null;
    
    // 增加随机访问性
    e.recordAccess(this);
    return e.value;
}

/**
* Entry类中的方法
**/
void recordAccess(HashMap<K,V> m) {
    LinkedHashMap<K,V> lm = (LinkedHashMap<K,V>)m;
    // 如果允许随机访问则移除此节点,再把该节点重新放在双向链表的header前面
    if (lm.accessOrder) {
        lm.modCount++;
        remove();
        addBefore(lm.header);
    }
}
```
