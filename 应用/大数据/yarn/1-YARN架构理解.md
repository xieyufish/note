## YARN架构

Hadoop YARN又名MapReduce NextGen和MRv2。YARN的基本思想是把**集群资源管理功能**和**任务调度监控功能**分为两个独立的进程，就是基于这种思想产生了一个全局**ResourceManager(RM)**和基于每个应用而产生的**ApplicationMaster(AM)**。

**RecourseManager**和**NodeManager**形成了数据计算的框架。**RecourseManager**是集群中所有应用资源调度的最终仲裁者，**NodeManager**作为框架的一个代理运行在集群中每台机器上，主要负责容纳和监控每台机器上的资源(cpu，memory，disk，network等)，并将机器的资源情况报告给ResourceManager上的Scheduler模块。

**ApplicationMaster**是一个特殊的架构，主要负责和RM进行谈判，从RM获取分配给应用的集群资源，并且和NodeManager节点一起执行和监控应用中任务的执行情况。

YARN的架构用图表述如下： 

![yarn_1](images\yarn_1.gif)

ResourceManager有两个主要组件：**Scheduler**和**ApplicationManager**。

**Scheduler**负责给每个运行中的应用分配集群资源，但并不负责监控和跟踪应用的执行状态，也并不提供应用自身或者硬件引起的应用失败重启；Scheduler只根据应用的资源需求执行资源调度函数，这个功能是基于*Container*(包含cpu，memory，disk，network等资源元素)这个抽象概念实现的。目前，YARN中包含有两种Scheduler：分别为CapacityScheduler和FairScheduler。

**ApplicationManager**负责接受任务提交，选择一个运行ApplicationMaster进程的*Container*，并提供当ApplicationMaster失败时重启ApplicationMaster的服务。而基于每个应用的ApplicationMaster则负责和RM上的Scheduler协商获取运行应用所需的资源以及跟踪监控应用的执行状态。



