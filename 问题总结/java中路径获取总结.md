## getClass().getResource()等获取文件的方法总结

首先要明白的一点是：所有方法的路径都是针对的类路径，也就是说是编译后的.class文件所在路径来说的。项目结构如下：

 ![2](images\2.png)

 ![3](images\3.png)

![4](images\4.png)

结果：

 ![5](images\5.png)

1. this.getClass().getResource("")：返回的是包含类全名的路径，一直是com.shell.test包的路径，也就是：E:/mavenalgorithm/target/test-classes/com/shell/test/


2. this.getClass().getResource("/")：返回的是项目配置的classpath路径，也就是：E:/mavenalgorithm/target/test-classes


3. this.getClass().getResourceAsStream("1.txt")：获取的是当前类所在目录下的文件1.txt, 也就是：E:/mavenalgorithm/target/test-classes/com/shell/test/1.txt ![6](images\6.png)

4. this.getClass().getResourceAsStream("/1.txt")：获取的是项目存放class文件的根目录下的1.txt, 也就是：E:/mavenalgorithm/target/test-classes/1.txt

    ![7](C:\Users\Administrator\Desktop\note\java\images\7.png)

5. this.getClass.getClassLoader().getResource()跟this.getClass().getResource("/")的用处一；同理：this.getClass().getClassLoader().getResourceAsStream("")跟this.getClass().getResourceAsStream("/")是一样的

6. this.getClass.getClassLoader().getResource("/")会报错

   同理：this.getClass().getClassLoader().getResourceAsStream("/")会报错

   也就是说this.getClass().getClassLoader()执行之后已经是定位到了项目配置的classpath路径了

   this.getClass().getResource("/")和this.getClass().getResourceAsStream("/")一样的是定位到了项目的classpath下





