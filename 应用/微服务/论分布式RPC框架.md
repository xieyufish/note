## 论分布式RPC框架

RPC，即 Remote Procedure Call（远程过程调用），说得通俗一点就是：调用远程计算机上的服务，就像调用本地服务一样。

RPC 可基于 HTTP 或 TCP 协议，Web Service 就是基于 HTTP 协议的 RPC，它具有良好的跨平台性，但其性能却不如基于 TCP 协议的 RPC。在两方面会直接影响 RPC 的性能，一是传输方式，二是序列化。

众所周知，TCP 是传输层协议，HTTP 是应用层协议，而传输层较应用层更加底层，在数据传输方面，越底层越快，因此，在一般情况下，TCP 一定比 HTTP 快。就序列化而言，Java 提供了默认的序列化方式，但在高并发的情况下，这种方式将会带来一些性能上的瓶颈，于是市面上出现了一系列优秀的序列化框架，比如：Protobuf、Kryo、Hessian、Jackson 等，它们可以取代 Java 默认的序列化，从而提供更高效的性能。

下面将对领域中比较常见的RPC框架进行简单的介绍：

[Dubbo](http://dubbo.io/) 是阿里巴巴公司开源的一个Java高性能优秀的服务框架，使得应用可通过高性能的 RPC 实现服务的输出和输入功能，可以和 Spring框架无缝集成。不过，略有遗憾的是，据说在淘宝内部，dubbo由于跟淘宝另一个类似的框架HSF（非开源）有竞争关系，导致dubbo团队已经解散，反到是当当网的扩展版本仍在持续发展，墙内开花墙外香。其它的一些知名电商如当当、京东、国美维护了自己的分支或者在dubbo的基础开发，但是官方的库缺乏维护，相关的依赖类比如Spring，Netty还是很老的版本(Spring 3.2.16.RELEASE, netty 3.2.5.Final),倒是有些网友写了升级Spring和Netty的插件。

[Motan](https://github.com/weibocom/motan)是新浪微博开源的一个Java 框架。它诞生的比较晚，起于2013年，2016年5月开源。Motan 在微博平台中已经广泛应用，每天为数百个服务完成近千亿次的调用。

[rpcx](https://github.com/smallnest/rpcx)是Go语言生态圈的Dubbo， 比Dubbo更轻量，实现了Dubbo的许多特性，借助于Go语言优秀的并发特性和简洁语法，可以使用较少的代码实现分布式的RPC服务。

[gRPC](http://www.grpc.io/)是Google开发的高性能、通用的开源RPC框架，其由Google主要面向移动应用开发并基于HTTP/2协议标准而设计，基于ProtoBuf(Protocol Buffers)序列化协议开发，且支持众多开发语言。本身它不是分布式的，所以要实现上面的框架的功能需要进一步的开发。

[thrift](https://thrift.apache.org/)是Apache的一个跨语言的高性能的服务框架，也得到了广泛的应用。

[Wildfly](http://www.wildfly.org/)是JBossAS改名后的JBoss应用服务器，实现了完整的JavaEE规范。我们知道JavaEE中远程RPC调用是在EJB规范中定义的。我们这里就是要测试Wildlfy中的远程EJB调用能力。

[EAP](http://www.jboss.org/products/eap/download/)是JBossAS的商业版本，实现了完整的JavaEE规范。EAP6基于AS7.2以后的版本构建，红帽提供商业支持。

[TChannel](https://github.com/uber/tchannel)是Uber开源的一个RPC服务框架。

[ZeroC ice](https://zeroc.com/)基于GPLv2协议开源的老牌RPC服务框架，用于商业得要购买许可证。

[Finagle](https://twitter.github.io/finagle/)由Twitter开发开源的专为Java/Scala而生的成熟的RPC框架

|               |                  Motan                   |                  Dubbo                   |        gRPC        |   Thrift    |
| ------------- | :--------------------------------------: | :--------------------------------------: | :----------------: | :---------: |
| **配置方式**      |                xml配置、注解配置                |          xml配置、注解配置、属性配置、api配置           |       api配置        |    api配置    |
| **服务器通信协议**   |                 Motan协议                  | Dubbo、Rmi、Hessian、HTTP、WebService、Dubbo Thrift、Memcached |     HTTP/2.0协议     |   Socket    |
| **序列化**       |              hessian2、Json               |            hessian2、java、json            |      protobuf      |   thrift    |
| **负载均衡**      | ActiveWeight、Random、RoundRobin、LocalFirst、Consistent、ConfigurableWeight | Random、RoundRobin、ConsistentHash、LeastActive |     可插拔负载均衡器机制     |      -      |
| **容错**        |            Failover、Failfast             | Failover、Failfast、Failsafe、Failback、Forking、Broadcast |      Failover      |      -      |
| **注册中心与服务发现** |          Consul、Zookeeper、点对点直连          |     Zookeeper、Redis、Multicas、Simple      |         -          |      -      |
| **多语言支持**     |           支持phpclient和C server           |                   java                   |         支持         |     支持      |
| **社区活跃度**     |                    活跃                    |                    停滞                    |         活跃         |     活跃      |
| **性能**        |                    2                     |                    2                     |         3          |     1-快     |
| **文档支持**      |                 中文文档齐全详细                 |                 中文文档齐全详细                 | 英文文档(有中文翻译版本)，不够详细 | 英文文档，指导不够详细 |
| **学习成本**      |                    2                     |                   1-低                    |        4-高         |      3      |
| **服务治理与管理**   |                    1                     |                   1-低                    |         2          |     3-高     |

性能测试结果

![snipaste_20170224_105130](images\snipaste_20170224_105130.png)