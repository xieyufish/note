## java源码解析-TreeMap类

TreeMap：基于红黑树实现的一个有序的Map实现类。这个有序的维护是通过key实现的Comparable接口或者是在构造时传入的Comparator类来实现它的一个排序规则的。TreeMap的实现保证了containsKey()，put()，get()，remove()操作的时间复杂度均是log(n)(n是树上的entry数)。TreeMap是非线程安全类，如果多个线程同时访问来修改treemap的结构(改变结构是指执行了添加或者删除操作，如果改变一个已经存在的key的值这类操作则不算是改变结构)，那么必须在外部来保证对这个treemap的同步访问。
如果要对一个treemap进行同步访问，我们也可以使用java中提供的同步包装类来实现同步，例如：

```java
SortedMap m = Collections.synchronizedSortedMap(new TreeMap(...));
```
下面我们来看看TreeMap中的Entry是一种什么样的结构：
```java
static final class Entry<K,V> implements Map.Entry<K,V> {
    K key; 
    V value;
    Entry<K,V> left = null;  // 左子树
    Entry<K,V> right = null;  // 右子树
    Entry<K,V> parent;  // 父节点
    boolean color = BLACK;  // 本节点的颜色(红黑两种)
    
    Entry(K key, V value, Entry<K,V> parent) {
        this.key = key;
        this.value = value;
        this.parent = parent;
    }
}
```
### 类定义
```java
public class TreeMap<K,V>
    extends AbstractMap<K,V>
    implements NavigableMap<K,V>, Cloneable, java.io.Serializable
```
### 成员变量
| 修饰符                                 | 变量名          | 作用        |
| ----------------------------------- | ------------ | --------- |
| private final Comparator<? super K> | comparator   | 排序比较类     |
| private transient Entry\<K,V>       | root = null  | 红黑树的根节点   |
| private transient int               | size = 0     | entry数量   |
| private transient int               | modCount = 0 | 记录改变结构的次数 |

### 方法讲解-只讲解put和get操作
> **添加元素 put()**  

```java
/**
* 1. 首先判断根元素是否为空,也就是当前put进来的entry是否是第一次操作
* 2. 如果是第一次put,则直接创建entry并赋值给root
* 3. 如果不是第一次put(也就是root不为null),则获取比较器
* 4. 将新增的key从根节点开始比较,
*    小于根节点则继续跟当前节点的左子树的根节点比较;
*    大于根节点则继续跟当前节点的右子树的根节点比较;
*    如果等于当前节点则直接用value替换原来的值并返回原来的值
* 5. 最后如果不是执行的替换操作,而是执行的插入则要重新调整树的结构,
*    让新树符合红黑树的规则.
**/
public V put(K key, V value) {
    Entry<K,V> t = root;
    if (t == null) {
        compare(key, key); // type (and possibly null) check

        root = new Entry<>(key, value, null);
        size = 1;
        modCount++;
        return null;
    }
    int cmp;
    Entry<K,V> parent;
    // split comparator and comparable paths
    Comparator<? super K> cpr = comparator;
    if (cpr != null) {
        do {
            parent = t;
            cmp = cpr.compare(key, t.key);
            if (cmp < 0)
                t = t.left;
            else if (cmp > 0)
                t = t.right;
            else
                return t.setValue(value);
        } while (t != null);
    }
    else {
        if (key == null)
            throw new NullPointerException();
        Comparable<? super K> k = (Comparable<? super K>) key;
        do {
            parent = t;
            cmp = k.compareTo(t.key);
            if (cmp < 0)
                t = t.left;
            else if (cmp > 0)
                t = t.right;
            else
                return t.setValue(value);
        } while (t != null);
    }
    
    // 遍历完成,找到新增节点放置的位置
    Entry<K,V> e = new Entry<>(key, value, parent);
    if (cmp < 0)  // 比父节点小,放在左子树
        parent.left = e;
    else   // 大,放在右子树
        parent.right = e;
    
    // 重新调整树使之符合红黑树的规则
    // (此过程比较复杂,需了解红黑树算法规则,在此暂不分析)
    fixAfterInsertion(e);
    size++;
    modCount++;
    return null;
}
```
> **获取元素get()**  
> TreeMap的get()操作就是通过比较循环获取左右子树比较的一个过程，直到找到对应的节点

```java
public V get(Object key) {
    Entry<K,V> p = getEntry(key);
    return (p==null ? null : p.value);
}

final Entry<K,V> getEntry(Object key) {
    // Offload comparator-based version for sake of performance
    if (comparator != null)
        return getEntryUsingComparator(key);
    if (key == null)
        throw new NullPointerException();
    Comparable<? super K> k = (Comparable<? super K>) key;
    Entry<K,V> p = root;
    while (p != null) {
        int cmp = k.compareTo(p.key);
        if (cmp < 0)
            p = p.left;
        else if (cmp > 0)
            p = p.right;
        else
            return p;
    }
    return null;
}

final Entry<K,V> getEntryUsingComparator(Object key) {
    K k = (K) key;
    Comparator<? super K> cpr = comparator;
    if (cpr != null) {
        Entry<K,V> p = root;
        while (p != null) {
            int cmp = cpr.compare(k, p.key);
            if (cmp < 0)
                p = p.left;
            else if (cmp > 0)
                p = p.right;
            else
                return p;
        }
    }
    return null;
}
```
