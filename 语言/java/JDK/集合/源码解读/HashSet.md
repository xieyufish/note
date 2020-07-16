## java源码解析-HashSet类

Java中set集合接口：用于存储没有重复元素(equals()和hashCode())的集合，可以存储null元素。

下面详细介绍HashSet  
HashSet实现方式：通过hash索引的方式来存储和取值，内部通过维护一个HashMap来实现的，我们知道HashMap的key是唯一的不能重复的，并且也是通过hash方式来索引的，这一点特性恰好是跟HashSet不谋而合，所以HashSet是直接通过HashMap来实现的。他的所有操作都是间接调用的HashMap方法来实现的
### 类定义

```java
public class HashSet<E>
    extends AbstractSet<E>
    implements Set<E>, Cloneable, java.io.Serializable
```

### 成员变量
| 修饰符                                  | 变量名                    | 作用                |
| ------------------------------------ | ---------------------- | ----------------- |
| private transient HashMap\<E,Object> | map                    | 内部维护的map          |
| private static final Object          | PRESENT = new Object() | 存放在map中的值对象,做占位而已 |

### 构造方法
```java
public HashSet() {
    map = new HashMap<>();
}

public HashSet(int initialCapacity) {
    map = new HashMap<>(initialCapacity);
}
```

### 方法
只列举几个方法，他的具体实现请参考HashMap讲解

```java
public Iterator<E> iterator() {
    return map.keySet().iterator();
}

public int size() {
    return map.size();
}

public boolean contains(Object o) {
    return map.containsKey(o);
}
```
