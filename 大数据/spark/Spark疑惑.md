## Spark疑惑

1. spark跟hadoop的hdfs需不要结合使用?结合使用的话, 那么他们之间是怎么通信交流的?
2. spark的三种部署模式: Standalone, YARN, Mesos; spark任务是不是必须要打包成jar包, 每次都通过spark-submit.sh命令来提交任务?
3. 针对Spark中的RDD，如它文档中所说的，每个RDD我们均可以执行持久化操作，让这个RDD保存在内存或者磁盘上，但是，正如Spark是处理大数据集的，一个RDD肯定是非常庞大的，不如说几个G甚至几个T那么大，那么大的数据再一次保存到内存或者磁盘上肯定会造成内存溢出或者是浪费磁盘空间呀？