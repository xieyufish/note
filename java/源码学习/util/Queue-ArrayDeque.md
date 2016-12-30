## java源码解析-Queue类

Java中队列接口，队列集合设计的主要目的是来存储元素之间有优先处理权的元素。Queue接口处了Collection接口中定义的操作之外，另外增添了具有队列特色的操作。比如：

- remove()- 删除队列的头元素， 如果队列是空，则抛出异常
- poll()- 删除队列的头元素，如果队列是空，不会抛出异常返回null
- element()- 返回队列的头元素，如果队列是空，抛出异常
- peek()- 返回队列的头元素，如果队列是空，不会抛出异常返回null
- add()- 如果队列已满会抛出异常表示插入失败
- offer()- 通过抛出异常来表示插入异常

java中提供了ArrayDeque和LinkedList两个类来实现队列。 
ArrayDeque以数组的方式实现双向队列，LinkedList以链表方式实现队列。 
java中队列的实现是有点混乱的，如果你想要这两个类实现我们队列的那种特性，那么必须要用好配对的方法，比如在ArrayDeque中add()方法跟remove()方法要配套使用才会有那种先进先出的机制等，而在LinkedList中，add()和remove()方法是list的特性。

在ArrayDeque中，removeFirst()方法并不一定是移除你第一次添加的元素，他移除的只是head指向的索引处的元素。