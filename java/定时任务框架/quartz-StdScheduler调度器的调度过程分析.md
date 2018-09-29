## 定时任务框架Quartz之StdScheduler实现的调度过程详解

###一、调度器的种类

![](images\Scheduler.png)

在Quartz中，所有调度器都是接口Scheduler的实现类，有三种实现：

- StdScheduler：最常用的实现，它的所有的方法调用都是直接通过调用内部的QuartzScheduler实例来完成的；
- RemoteScheduler：所有方法调用都是直接通过内部的RemotableQuartzScheduler实例通过RMI方式来完成的；
- RemoteMBeanScheduler：Scheduler的一个抽象实现类，用户如果使用这个抽象类，那么必须自己创建子类来实现到远程MBeanServer的连接。

### 二、调度器的调度过程

下面以最常用的StdScheduler为例，对Quartz的任务调度过程进行分析。

``````java
package org.quartz.examples.example1;

import static org.quartz.DateBuilder.evenMinuteDate;
import static org.quartz.JobBuilder.newJob;
import static org.quartz.TriggerBuilder.newTrigger;

import org.quartz.JobDetail;
import org.quartz.Scheduler;
import org.quartz.SchedulerFactory;
import org.quartz.Trigger;
import org.quartz.impl.StdSchedulerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;

/**
 * This Example will demonstrate how to start and shutdown the Quartz scheduler and how to schedule a job to run in
 * Quartz.
 * 
 * @author Bill Kratzer
 */
public class SimpleExample {

  public void run() throws Exception {
    Logger log = LoggerFactory.getLogger(SimpleExample.class);

    log.info("------- Initializing ----------------------");

    // 获取调度器
    SchedulerFactory sf = new StdSchedulerFactory();
    Scheduler sched = sf.getScheduler();

    log.info("------- Initialization Complete -----------");

    // 计算当前时间的下一分钟
    Date runTime = evenMinuteDate(new Date());

    log.info("------- Scheduling Job  -------------------");

    // 定义一个任务
    JobDetail job = newJob(HelloJob.class).withIdentity("job1", "group1").build();

    // 创建一个在下一分钟开始的触发器
    Trigger trigger = newTrigger().withIdentity("trigger1", "group1").startAt(runTime).build();

    // 告诉调度器使用我们创建的触发器来调度任务
    // 这一步会将job信息，trigger信息存放在Quartz指定的存储器中（默认是RAMJobStore内存存储）
    sched.scheduleJob(job, trigger);
    log.info(job.getKey() + " will run at: " + runTime);

    // Start up the scheduler (nothing can actually run until the
    // scheduler has been started)
    // 启动调度器，在调度器启动之前，什么事情都不会做
    sched.start();

    log.info("------- Started Scheduler -----------------");

    // wait long enough so that the scheduler as an opportunity to
    // run the job!
    log.info("------- Waiting 65 seconds... -------------");
    try {
      // wait 65 seconds to show job
      Thread.sleep(65L * 1000L);
      // executing...
    } catch (Exception e) {
      //
    }

    // shut down the scheduler
    log.info("------- Shutting Down ---------------------");
    sched.shutdown(true);
    log.info("------- Shutdown Complete -----------------");
  }

  public static void main(String[] args) throws Exception {

    SimpleExample example = new SimpleExample();
    example.run();

  }

}
``````

####A. StdScheduler的初始化

![](images\StdScheduler初始化.png)

1. 首先，我们通过工厂类来获取或者创建一个调度器，对应不同类型的调度器，Quartz提供了两个工厂类：StdSchedulerFactory和DirectSchedulerFactory；new一个StdSchedulerFactory工厂类。
2. 创建了工厂类实例之后，通过StdSchedulerFactory的getScheduler()实例方法获取调度器实例：
   - 第一步：如果初始化工厂类时没有指定配置文件，那么首先查找Quartz的配置文件位置，查找顺序：
     - 系统属性org.quartz.properties指定的文件；
     - 类路径下命名为quartz.properties的文件；
     - 默认的org.quartz包下的quartz.properties文件；
   - 第二步：获取SchedulerRepository调度器仓库实例，在调度器仓库中，根据quartz.properties中配置的调度器名称查找是否已经存在同名的Scheduler实例，如果存在且没有shutdown则直接返回，否则继续下一步；
   - 第三步：根据配置文件实例化一个调度器；
     - 根据配置文件确定创建的调度器类型：RemoteScheduler(org.quartz.scheduler.rmi.proxy=true)、RemoteMBeanScheduler(org.quartz.scheduler.jmx.proxy=true)或者是StdScheduler(默认，下面过程是以这个为例)；
     - 创建JobFactory工厂实例(由配置org.quartz.scheduler.jobFactory.class指定具体类，没有指定时取默认值：org.quartz.simpl.PropertySettingJobFactory.PropertySettingJobFactory)，在触发器被触发时，负责创建具体的Job实例，也就是在具体项目中要实现的；
     - 创建InstanceIdGenerator实例，负责生产调度器的唯一标识id；
     - 创建ThreadLocal线程池实例，并初始化一些属性值，由配置org.quartz.scheduler.jobFactory.class指定具体的线程池实现类，默认值org.quartz.simpl.SimpleThreadPool，负责执行具体的任务，注意这里还只是初始化的一个实例，还并没有创建具体的工作线程对象；
     - 创建JobStore任务存储器实例，由配置org.quartz.jobStore.class指定具体的存储实现类，默认值为org.quartz.simpl.RAMJobStore(将任务信息存放在内存中)，负责用户创建的定时任务信息的存储工作，还有一个实现类为org.quartz.impl.jdbcjobstore.JobStoreSupport(抽象类，将任务信息基于JDBC持久化)；
     - 如果JobStore是JobStoreSupport实现，那么接下来会做一些锁处理器以及数据源的一些初始化配置工作；
     - 调度器的插件安装；
     - JobListener、TriggerListener的初始化工作；
     - 线程执行器ThreadExecutor的创建，由配置org.quartz.threadExecutor.class指定，默认实现为：org.quartz.impl.DefaultThreadExecutor.DefaultThreadExecutor；
     - 创建JobRunShellFactory工厂实例，负责具体任务执行时的封装；
     - 创建QuartzSchedulerResources实例，负责将所有的根据配置信息创建的实例对象保管起来；
     - 工作线程创建，根据配置文件中配置的工作线程数创建具体的工作线程；
     - 创建QuartzScheduler实例，QuartzScheduler是具体任务调度的实现类，Scheduler的实现类里面封装的就是QuartzScheduler，通过Scheduler的调度最终委托给QuartzScheduler处理，在实例化QuartzScheduler的过程中，会启动QuartzShcdulerThread调度线程，此调度线程在Scheduler调用方法start()之前会一直循环阻塞等待，这也是为什么在Scheduler.start()之前，调度器什么都不会做的原理；
     - 将QuartzScheduler实例传递给StdScheduler构造方法，创建Scheduler实例；
     - 将必要的属性实例值绑定到Scheduler实例；
     - 返回Scheduler实例；
   - 返回Scheduler实例。

#### B. 任务提交

![](images/任务提交流程.png)

1. 通过Scheduler的scheduleJob(Trigger)或者schedulerJob(JobDetail, Trigger)方法，将定时任务提交给调度器，在具体的调度器实现中最终会委托给QuartzScheduler实例的scheduleJob(Trigger)或者schedulerJob(JobDetail, Trigger)方法；
2. 在QuartzScheduler的scheduleJob方法中，首先对调度器的当前状态进行判断，如果调度器已经被关闭则抛出异常，终止任务的提交，否则继续执行下一步；
3. 对提交的JobDetail和Trigger进行验证，如果两者都不为空，且Trigger绑定的job是jobdetail表示的且Trigger的name、group、jobname和jobgroup属性不为null，则继续进入下一步，否则抛出异常，终止任务的提交；
4. 计算Trigger的第一次触发时间ft，如果ft==null，说明Trigger指定的触发时间已经过去了，这个任务永远都不会被触发了，则抛出异常，终止任务提交；否则进入下一步；
5. 调用在初始化时配置的JobStore，将JobDetail和Trigger放入对应的存储器中；
6. 通知SchedulerListener监听器有新任务加入；
7. 通知SchedulerThread(QuartzSchedulerThread)调度器调度线程，改变调度状态，这个状态的变化在我们分析具体的调度时非常有用；
8. 通知SchedulerListener监听器有新任务被调度；

其中，Quartz提供的默认存储器RAMStore存储任务的结构如下：

![](images/RAMStore存储任务.png)

**jobsByGroup：**HashMap结构，存放JobDetail数据；其中以JobDetail设置的组名group为key，value值则是一个HashMap，value的HashMap以JobDetail的jobkey(由jobname和group组成)为键，值则是JobDetail的包装类(JobWrapper)；

**triggersByGroup：**HashMap结构，存放Trigger数据；与jobsByGroup的结构一样，先以Trigger的所属组名group为key，value则是一个以triggerKey(由triggerName和group组成)为键，Trigger的包装类TriggerWrapper为值的HashMap;

**jobsByKey：**一个以jobkey为键，JobWrapper为值的HashMap结构；

**triggersByKey：**一个以triggerKey为键，TriggerWrapper为值的HashMap结构；

**triggersByJob：**一个以Trigger绑定的jobDetail的jobkey为键，jobList(元素为TriggerWrapper的ArrayList)为值的HashMap结构；

**timeTriggers：**按Trigger的下一次激活触发时间(nextFireTime)升序排序的TreeSet结构，存放可以被激活触发的TriggerWrapper，这个TreeSet里存放的数据也就是要被QuartzSchedulerThread线程调度的；

#### C. 任务调度

![](images/任务调度流程.png)













