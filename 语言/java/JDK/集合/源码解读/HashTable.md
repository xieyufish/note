## java源码解析-HashTable类

HashTable跟HashMap的作用是一样的，基本的实现方式也差不多，所以这一讲我不会再对代码进行详细的分析。只不过HashTable是线程同步的。所有的实例方法都是synchronized的。同时HashTable中存储的键值对是不允许为空的(键和值都不能为空)，所以如果put进的键值含有null值，将会抛出空指针异常。同时两个针对key做的hash算法是不一样的，根据hash值来计算在数组中的索引算法也是不一样的。由于HashTable继承自Dictionary类，所以他有额外的枚举遍历方法。

### HashTable跟HashMap对比:
| 比较项  | HashTable  | HashMap     |
| ---- | ---------- | ----------- |
| 线程   | 多线程安全的     | 线程不安全的      |
| 键值   | 均不允许为null  | 可以为null     |
| 继承   | Dictionary | AbstractMap |

### 总结
从代码结构上可以看的出，HastTable在方法定义细化上没有HashMap做的，HashMap的每个方法的功能做的都很细致，分的很细。不过在迭代器的实现上面HashTable做的比HashMap要perfect。