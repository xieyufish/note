## java源码解析-Future类

Future是在jdk1.5之后才加入进来，属于java.util.concurrent.*框架中的一个接口。

Future接口定义了一个异步计算结果的规则。在Future类中的方法可以用来检查计算是否完成，等待计算完成，取得计算结果。结果只能通过方法get()来获取，他会阻塞等待计算结果；同时也可以取消一个异步计算任务，但是这个任务必须是要没有开始执行的。如果我们只想用Future来实现一个可以取消的任务而不是要获取一个可用的计算结果，那么我们可以声明为Future<?>，然后再get()方法中返回null。 
Future的使用方式如下：

```java
1. 直接使用Future,从ExecutorService中返回一个Future实例
interface ArchiveSearcher { String search(String target); }
class App {
  ExecutorService executor = ...
  ArchiveSearcher searcher = ...
  void showSearch(final String target)
      throws InterruptedException {
    Future<String> future
      = executor.submit(new Callable<String>() {
        public String call() {
            return searcher.search(target);
        }});
    displayOtherThings(); // do other things while searching
    try {
      displayText(future.get()); // use future
    } catch (ExecutionException ex) { cleanup(); return; }
  }
}

2. 使用FutureTask类,FutureTask是RunnableFuture接口的一个基本实现.
FutureTask<String> future =
new FutureTask<String>(new Callable<String>() {
 public String call() {
   return searcher.search(target);
}});
executor.execute(future);
```

### 方法定义
下面看看Future接口里面定义的方法, 他的具体实现将在FutureTask中分析

> boolean cancel(boolean mayInterruptIfRunning);  
> 取消一个异步计算任务

> boolean isCancelled();  
> 判断任务是否被取消

> boolean isDone();
> 判断任务是否完成, 任务完成的状态标志包括:正常结束, 出现异常, 被取消

> V get() throws InterruptedException, ExecutionException;  
> 获取异步任务结果

> V get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException;  
> 在给定的时间范围内获取异步任务结果

RunnableFuture(V)继承自Future和Runnable，使得Future是可执行的。