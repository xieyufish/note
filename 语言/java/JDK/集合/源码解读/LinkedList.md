## java源码解析-LinkedList类

**类定义**  

```java
public class LinkedList<E>
    extends AbstractSequentialList<E>
    implements List<E>, Deque<E>, Cloneable, java.io.Serializable
```
从类的定义我们可以直观的知道LinkedList拥有List和Deque(双向队列)的双重特性。也就是说LinkedList是一种双向链表的实现方式，大家都知道链表的一个基本实现方式是通过每个节点之间前后指向关系来体现的，在Java中也一样，LinkedList拥有一个内部实现类Node，通过Node的next，prev属性连保持各个节点之间的联系。下面我将从我的理解，详细讲解LinkedList的实现方式。
**效率：** 插入都快速，查找都缓慢(下标查找方式时，会使用折半再查找的方式)

### 成员变量属性
| 修饰符                     | 变量       | 作用                         |
| ----------------------- | -------- | -------------------------- |
| transient int           | size     | 默认的初始化数组存储空间大小             |
| transient Node\<E>      | first    | 指向链表的第一个节点                 |
| transient Node\<E>      | last     | 指向链表的最后一个节点                |
| protected transient int | modCount | 继承自AbstractList,记录size变化的次 |

**内部类Node结构**

```java
private static class Node<E> {
    E item;
    Node<E> next;
    Node<E> prev;

    Node(Node<E> prev, E element, Node<E> next) {
        this.item = element;
        this.next = next;
        this.prev = prev;
    }
}
```
Node的结构很简单，就是维护一个当前节点的值，next指向下一个节点，prev指向前一个节点

### 队列方法
> **public void addFirst(E e)**  
> 前端插入数据

> **public void addLast(E e)**  
> 后端插入数据

> **根据下标查找会折半**  

```java
public E get(int index) {
    checkElementIndex(index);
    return node(index).item;
}

Node<E> node(int index) {
    // assert isElementIndex(index);

    if (index < (size >> 1)) {
        Node<E> x = first;
        for (int i = 0; i < index; i++)
            x = x.next;
        return x;
    } else {
        Node<E> x = last;
        for (int i = size - 1; i > index; i--)
            x = x.prev;
        return x;
    }
}
```

查看源码发现并没有难点问题需要记录，故在此不再赘述，需要注意的地方在ArrayList中已说明