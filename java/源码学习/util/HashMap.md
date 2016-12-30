## java源码解析-HashMap类

Map是java中用于存储建值对的一种数据结构方式。键不能重复，每一个键可以匹配多个值(也就是一个链表)。这个接口是用于替换Dictionary这个抽象类的。

HashMap用于存储\<key, value>键值对，其中key可以为null，同时他的key存放索引方式是通过hash方式来实现的，所以他能快速的定位到你需要的key处。在HashMap内部是存放的一个Entry的数组。
Entry的定义如下：
```java
Entry(int h, K k, V v, Entry<K,V> n) {
    value = v;
    next = n;
    key = k;
    hash = h;
}
```


### 类定义
```java
public class HashMap<K,V>
    extends AbstractMap<K,V>
    implements Map<K,V>, Cloneable, Serializable
```

### 成员变量
| 修饰符                           | 变量名                           | 作用                                       |
| ----------------------------- | ----------------------------- | ---------------------------------------- |
| transient Entry\<K,V>[] table | table                         | 存储数据的内部数组                                |
| static final Entry<?,?>[]     | EMPTY_TABLE                   | 空数组，用于初始化table                           |
| static final int              | MAXIMUM_CAPACITY = 1 << 30    | map存放数据最大容量                              |
| static final int              | DEFAULT_INITIAL_CAPACITY = 16 | map的默认初始化容量                              |
| transient int                 | size                          | map的存储数据大小，并不是table的length，而是Entry的数量    |
| static final float            | DEFAULT_LOAD_FACTOR = 0.75f   | 默认扩容因子，当$\frac{size}{capacity} \gt 0.75$时m，ap自动扩容同时执行rehash |
| int                           | threshold                     | map要扩容到的大小                               |
| final float                   | loadFactor                    | map扩容因子，通过构造方式传入进来                       |
| transient int                 | modCount                      | 记录map的size变化次数，跟list中的作用是一样的             |
| transient int                 | hashSeed = 0                  | 跟计算map的key索引hash值有关，但是我也还没明白(==补充：1.在resize()时判断是否需要重新计算hash值；2.用于计算key的hash值==) |

### 构造方式
> **public HashMap()**  
> 无参构造方式：用默认容量,默认扩容因子构造map

> **public HashMap(int initialCapacity)**  
> 用initialCapacity容量,默认扩容因子构造map

> **public HashMap(int initialCapacity, float loadFactor)**
> 用initialCapacity容量，loadFactor扩容因子构造map

> **public HashMap(Map<? extends K, ? extends V> m)**  
> 用其他map构造新的map

### 核心方法
> **添加元素public V put(K key, V value)**  

```java
public V put(K key, V value) {
    // 下面第一点讲解
    if (table == EMPTY_TABLE) {
        inflateTable(threshold);
    }
    // 下面第二点讲解
    if (key == null)
        return putForNullKey(value);
    
    // 取key的hash值,并用这个hash值来计算
    // 此键值对应该放置的数组中的索引(bucketIndex)
    int hash = hash(key);
    int i = indexFor(hash, table.length);
    
    // 根据key算出的索引,根据索引取得数组i处的Entry(链表)
    // 循环判断此链表中是否存在此key对应的节点
    for (Entry<K,V> e = table[i]; e != null; e = e.next) {
        Object k;
        // 如果节点e的hash值与key的hash值相等(就是比较的hashCode),
        // 也就是说发生了hash碰撞(key的hashCode跟e.key的hashCode是同一值)
        // 并且key相等(同一位置或者equals),也就是key已经存在对应节点
        // 那么就执行替换操作,并返回老的那个值
        if (e.hash == hash && ((k = e.key) == key || key.equals(k))) {
            V oldValue = e.value;
            e.value = value;
            e.recordAccess(this);
            return oldValue;
        }
    }
    
    // 1. 如果数组索引i处没有放置过任何值,也就是table[i]=null
    // 2. table[i]已经放置了Entry,但是hash值不相等(不可能)或者是key不equals()
    // 则新增一个Entry节点
    modCount++;
    addEntry(hash, key, value, i);
    return null;
}
```
代码分析：

**1. 如果当前map中没有任何元素，也就是说为空({})，那么就重新扩充table，inflateTable()方法源码如下：**
```java
private void inflateTable(int toSize) {
    // 将toSize向上取值到2的倍数
    int capacity = roundUpToPowerOf2(toSize);

    threshold = (int) Math.min(capacity * loadFactor, MAXIMUM_CAPACITY + 1);
    table = new Entry[capacity];  // 重新给table赋值
    initHashSeedAsNeeded(capacity);  // 重新计算hashSeed值
}
```
**2. 如果key为null，则专门处理key为null的情况，putForNullKey()代码如下：**
```java
private V putForNullKey(V value) {
    // 从下述循环代码可以看出, key为null的元素,
    // 在内部数组中是放置在索引为0处位置的
    // (可能key不为null也会放置在这里,取决hash的结果如何)
    for (Entry<K,V> e = table[0]; e != null; e = e.next) {
        if (e.key == null) {  // 查找链表中key为null的节点,找到并用新值替换
            V oldValue = e.value;
            e.value = value;
            e.recordAccess(this);
            return oldValue;
        }
    }
    
    // 如果在数组0处没有找到key为null的对应节点,则新增一个Entry节点
    modCount++;
    // key为null,hash直接取0,放置在数组中的位置也直接取0
    addEntry(0, null, value, 0);
    return null;
}

// 在数组索引bucketIndex处添加新的节点(注意是新增节点,并不一定是新增数组元素)
void addEntry(int hash, K key, V value, int bucketIndex) {
    // map当前容量达到了要扩容的值并且数组中待放置节点的元素位置已被占用
    // 则扩容,并重新计算新的节点要放置在数组中的索引位置
    if ((size >= threshold) && (null != table[bucketIndex])) {
        resize(2 * table.length);
        hash = (null != key) ? hash(key) : 0;
        bucketIndex = indexFor(hash, table.length);
    }
    // 创建新的节点,并放置在数组中索引为buckedIndex处
    createEntry(hash, key, value, bucketIndex);
}

// 创建新的Entry节点(一个链表), 并将新的节点重新挂到数组上
// 在这里要注意的: bucketIndex的计算方式是怎么计算来的,在上面有讲到
void createEntry(int hash, K key, V value, int bucketIndex) {
    // 获取之前挂在数组bucketIndex处的Entry节点
    Entry<K,V> e = table[bucketIndex]; 
    // new Entry<>(hash, key, value, e);
    // 创建一个新的节点,并跟原来的节点建立关系
    table[bucketIndex] = new Entry<>(hash, key, value, e);
    size++;
}
```

> **获取元素public V get(Object key)**  

```java
public V get(Object key) {
    // key为null,专门处理
    if (key == null)
        return getForNullKey();  //取得数组0处的Entry
    Entry<K,V> entry = getEntry(key);

    return null == entry ? null : entry.getValue();
}

final Entry<K,V> getEntry(Object key) {
    // map中没有元素返回null
    if (size == 0) {
        return null;
    }
    
    // 根据key的hash值取得对应key存放在数组中的位置
    int hash = (key == null) ? 0 : hash(key);
    for (Entry<K,V> e = table[indexFor(hash, table.length)];
         e != null;
         e = e.next) {
        Object k;
        if (e.hash == hash &&
            ((k = e.key) == key || (key != null && key.equals(k))))
            return e;
    }
    return null;
}
```

> **扩容void resize(int newCapacity)**  
> 在put()等增加size的操作中调用，在上述的addEntry()方法中被调用

```java
void resize(int newCapacity) {
    // 如果map容量已经达到Integer的最大值则不在扩容
    Entry[] oldTable = table;
    int oldCapacity = oldTable.length;
    if (oldCapacity == MAXIMUM_CAPACITY) {
        threshold = Integer.MAX_VALUE;
        return;
    }
    
    // 创建一个扩容后的新的空数组
    Entry[] newTable = new Entry[newCapacity];
    // 将map现在数组转移到新的空数组中
    // initHashSeedAsNeeded(newCapacity)用于确定在新数组
    // 每个key是否要重新计算hash值
    transfer(newTable, initHashSeedAsNeeded(newCapacity));
    table = newTable;
    threshold = (int)Math.min(newCapacity * loadFactor, MAXIMUM_CAPACITY + 1);
}

/**
 * Transfers all entries from current table to newTable.
 */
void transfer(Entry[] newTable, boolean rehash) {
    // 新数组的长度
    int newCapacity = newTable.length;
    // 对map中现有元素的老数组循环
    for (Entry<K,V> e : table) {
        // 取得的数组中元素不为空(对应索引处存在Entry链表)
        while(null != e) {
            // 将链表的下一个节点拎出来保存
            Entry<K,V> next = e.next;
            // 是否需要重新计算key的hash值
            if (rehash) {
                e.hash = null == e.key ? 0 : hash(e.key);
            }
            // 重新计算在新数组中的索引
            int i = indexFor(e.hash, newCapacity);
            // 将已经转换到新数组的entry拼接到当前处理entry的后面组成新的链表
            e.next = newTable[i];
            // 将新的链表重新放置在新数组的索引处
            newTable[i] = e;
            // 处理老数组链表的下一个entry
            e = next;
        }
    }
}
```
> **map迭代器Iterator核心private abstract class HashIterator\<E>**  
> map中的KeyIterator，ValueIterator等迭代器均继承自HashIterator。map中的迭代器跟list中的迭代器一样，都会根据size变化次数来fail-fast(快速失败检查)

```java
private abstract class HashIterator<E> implements Iterator<E> {
    Entry<K,V> next;        // next entry to return
    int expectedModCount;   // For fast-fail
    int index;              // current slot
    Entry<K,V> current;     // current entry

    HashIterator() {
        expectedModCount = modCount; // 赋值modCount
        // map有元素,就定位数组索引到第一个不为null的位置,并赋给next
        if (size > 0) { // advance to first entry
            Entry[] t = table;
            while (index < t.length && (next = t[index++]) == null)
                ;
        }
    }

    public final boolean hasNext() {
        return next != null;
    }

    final Entry<K,V> nextEntry() {
        // 在迭代期间, modCount值发生变化
        // (也就是map的size发生改变(执行了put, remove操作(迭代器的remove()除外)))
        if (modCount != expectedModCount)
            throw new ConcurrentModificationException();
        Entry<K,V> e = next;
        if (e == null)
            throw new NoSuchElementException();
        // 继续判断下一个entry,如果为空,则继续搜索数组下一个索引位置
        if ((next = e.next) == null) {
            Entry[] t = table;
            while (index < t.length && (next = t[index++]) == null)
                ;
        }
        // 返回当前的entry
        current = e;
        return e;
    }
    
    // 用迭代器执行删除操作(不会引起快速失败)
    public void remove() {
        if (current == null)
            throw new IllegalStateException();
        if (modCount != expectedModCount)
            throw new ConcurrentModificationException();
        Object k = current.key;
        current = null;
        HashMap.this.removeEntryForKey(k);
        expectedModCount = modCount;
    }
}
```

### [针对map常见面试问题](http://www.importnew.com/7099.html)
