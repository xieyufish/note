# Eureka

关于服务注册与发现，Netflix提供了一个Eureka的实现。在Spring Cloud中提供了一个抽象（你可以理解为提供了一个标准），分别为**ServiceRegistry接口**和**DiscoveryClient接口**，以及驱动**EnableDiscoveryClient注解**来表示接入服务发现。后来Spring Cloud基于Eureka提供了一个服务注册与发现的实现，类似的还提供了Consul和Zookeeper的相关实现支持。

:warning:按照Spring Cloud的标准，在应用中应该使用@EnableDiscoveryClient注解来表示接入了注册中心，但是这个需要相关实现包中提供在spring.factories文件提供了配置支持（EnableDiscoveryClient=xxxxxx），否则还是使用各自的驱动注解（如@EnableEurekaClient）；但其实对于客户端来说，EnableXXX注解可有可无，只需要相关的实现包在类路径下存在即可，springboot会自动读取spring.factories中的配置类启动注册服务和发现服务的相关任务。

本文主要介绍Eureka的实现原理。

## 一、客户端

### 1. 启动原理

按照常规做法，如果我们的springBoot应用想要接入Eureka注册中心，都会在启动类上增加`@EnableEurekaClient`注解；其实这个注解可加可不加，只要我们在pom.xml文件中引入如下配置即可：

```xml
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-netflix-eureka-client</artifactId>
    <version>${eureka.version}</version>
</dependency>
```

即将eureka的相关实现加入到classpath，之后springboot在启动过程中会读取META/spring.factories配置文件中的内容，自动加载相关配置类，其中核心配置类为：**EurekaClientAutoConfiguration**，其中定义了多个Bean注解的方法用于加载不同的bean，如：

1. EurekaInstanceConfigBean：表示一个Eureka实例的配置信息，用于描述一个实例的相关信息，比如name、ip、port、instanceId等，读取的就是我们在配置文件中设置的`eureka.instance`开头的相关配置；
2. EurekaClientConfigBean：表示一个Eureka客户端的配置信息，用于控制Eureka客户端的行为表现，比如是否要注册、抓取注册表的频率等等，读取的就是我们在配置文件中设置的`eureka.client`开头的相关配置；

还有其他如EurekaServiceRegistry、EurekaRegistration等相关的Bean。其中最重要的一个Bean定义是**CloudEurekaClient**，继承自Netflix中的**DiscoveryClient类**，用于实现服务的注册、发现、续约等核心的通信功能，而SpringCloud提供的Eureka实现EurekaDiscoveryClient类（实现了SpringCloud中的DiscoveryClient接口，所以如果在我们应用中注入DiscoveryClient时，其实现类是EurekaDiscoveryClient，最终是委托到CloudEurekaClient来实现相关功能的）的功能就是委托CloudEurekaClient来完成的。

### 2. 发现服务

前提开启了`eureka.client.fetch-registry`这个配置值为true。

即获取服务注册表，Eureka客户端是如何实现的呢？在**CloudEurekaClient**的构造方法中会调用Netflix中的父类**DiscoveryClient**的构造方法，在它的构造方法中有如下一段代码：

```java
// .....省略代码
if (clientConfig.shouldFetchRegistry() && !fetchRegistry(false)) {
    fetchRegistryFromBackup();	 // 抓取失败，则从备份获取注册信息
}
// .....省略代码
```

:warning:这里有一个备份注册信息的扩展点，fetch失败之后会从backup获取，Eureka默认备份是为Null（实现类为：**NotImplementedRegistryImpl**），我们可以提供一个备份实现类（实现自BackupRegistry接口），提供一个`eureka.client.backup-registry-impl`配置指定为我们自己的实现类即可。

上述这段代码的意思很明显，就是如果配置`eureka.client.fetch-registry`为true（默认值就是true，表示是否从Eureka server上获取服务注册信息），则执行`fetchRegistry()`来抓取服务注册信息，，其实现如下：

:warning:意味着，如果`fetch-registry`配置为false，则不会从服务端获取服务注册列表，也就无法发现服务。

```java
private boolean fetchRegistry(boolean forceFullRegistryFetch) {
    Stopwatch tracer = FETCH_REGISTRY_TIMER.start();

    try {
        Applications applications = getApplications();

        if (clientConfig.shouldDisableDelta()
            || (!Strings.isNullOrEmpty(clientConfig.getRegistryRefreshSingleVipAddress()))
            || forceFullRegistryFetch
            || (applications == null)
            || (applications.getRegisteredApplications().size() == 0)
            || (applications.getVersion() == -1)) //Client application does not have latest library supporting delta
        {
            //... 省略日志代码
            getAndStoreFullRegistry();	// 全量抓取并保存在本地的localRegionApps中
        } else {
            getAndUpdateDelta(applications);	// 增量抓取并更新本地缓存
        }
        applications.setAppsHashCode(applications.getReconcileHashCode());
        logTotalInstances();	// 记录日志而已
    } catch (Throwable e) {
        return false;
    } finally {
        if (tracer != null) {
            tracer.stop();
        }
    }
    onCacheRefreshed();	// 触发事件
    updateInstanceRemoteStatus();// 更新remote region实例的状态，针对远方region的，可以不用关注
    return true;
}
```

全量更新逻辑如下：

```java
private void getAndStoreFullRegistry() throws Throwable {
    // 获取当前的generation，防止多线程更新的问题
    long currentUpdateGeneration = fetchRegistryGeneration.get();

    Applications apps = null;
    // 向服务端发送请求，获取全量注册信息
    // 1. 如果配置了虚拟ip，则通过（serviceUrl+"vips/" + 虚拟ip）url获取注册信息
    // 2. 如果没有配置虚拟ip，则通过（serviceUrl + "apps/"）url获取注册信息
    EurekaHttpResponse<Applications> httpResponse = clientConfig.getRegistryRefreshSingleVipAddress() == null
        ? eurekaTransport.queryClient.getApplications(remoteRegionsRef.get())
        : eurekaTransport.queryClient.getVip(clientConfig.getRegistryRefreshSingleVipAddress(), remoteRegionsRef.get());
    if (httpResponse.getStatusCode() == Status.OK.getStatusCode()) {
        apps = httpResponse.getEntity();	// 获取成功
    }

    if (apps == null) {
        // 打印日志
    } else if (fetchRegistryGeneration.compareAndSet(currentUpdateGeneration, currentUpdateGeneration + 1)) {
        // 无多线程情况，则对获取的服务注册信息进行过滤和shuffle操作
        localRegionApps.set(this.filterAndShuffle(apps));
    } else {
        // 打印日志
    }
}
```

![image-20201014155357604](E:\study\资料文档\笔记\spring\images\image-20201014155357604.png)

增量更新逻辑如下：

```java
private void getAndUpdateDelta(Applications applications) throws Throwable {
    long currentUpdateGeneration = fetchRegistryGeneration.get(); // 多线程控制

    Applications delta = null;
    // 通过（serviceUrl + "apps/delta"）url获取增加更新数据
    EurekaHttpResponse<Applications> httpResponse = eurekaTransport.queryClient.getDelta(remoteRegionsRef.get());
    if (httpResponse.getStatusCode() == Status.OK.getStatusCode()) {
        delta = httpResponse.getEntity(); // 增量更新请求成功
    }

    if (delta == null) {
        // 增量更新失败，则执行一个全量更新操作
        getAndStoreFullRegistry();
    } else if (fetchRegistryGeneration.compareAndSet(currentUpdateGeneration, currentUpdateGeneration + 1)) {
        // 无其他线程执行更新操作
        String reconcileHashCode = "";
        if (fetchRegistryUpdateLock.tryLock()) {
            try {
                // 执行更新
                // 根据返回数据的ActionType来做相应操作
                // ActionType.ADD、ActionType.MODIFIED、ActionType.DELETED
                updateDelta(delta);
                reconcileHashCode = getReconcileHashCode(applications);
            } finally {
                fetchRegistryUpdateLock.unlock();
            }
        } else {
            logger.warn("Cannot acquire update lock, aborting getAndUpdateDelta");
        }
        if (!reconcileHashCode.equals(delta.getAppsHashCode()) || clientConfig.shouldLogDeltaDiff()) {
            // 调和失败，会再次获取全量信息
            // 这里一个迷之行为，如果配置logDeltaDiff=true，那岂不是每次增量更新完了又再全量更新？
            reconcileAndLogDifference(delta, reconcileHashCode);
        }
    } else {
        // 打印日志
    }
}
```

![image-20201014160815108](E:\study\资料文档\笔记\spring\images\image-20201014160815108.png)

#### 定时更新任务

在构造函数中有如下代码：

```java
//...
initScheduledTasks();	// 初始化定时任务
//...

private void initScheduledTasks() {
    if (clientConfig.shouldFetchRegistry()) {
        int registryFetchIntervalSeconds = clientConfig.getRegistryFetchIntervalSeconds();
        int expBackOffBound = clientConfig.getCacheRefreshExecutorExponentialBackOffBound();
        // 定时刷新本地注册表缓存任务
        scheduler.schedule(
            new TimedSupervisorTask(
                "cacheRefresh",
                scheduler,
                cacheRefreshExecutor,
                registryFetchIntervalSeconds,
                TimeUnit.SECONDS,
                expBackOffBound,
                new CacheRefreshThread()
            ),
            registryFetchIntervalSeconds, TimeUnit.SECONDS);
    }
	//... 省略
}

class CacheRefreshThread implements Runnable {
    public void run() {
        refreshRegistry();
    }
}

void refreshRegistry() {
    //... 省略代码
 	boolean success = fetchRegistry(remoteRegionsModified); // 走增量更新逻辑
    if (success) {
        registrySize = localRegionApps.get().size();
        lastSuccessfulRegistryFetchTimestamp = System.currentTimeMillis();
    }
    //... 省略代码
}
```

关键配置：

1. `eureka.client.registryFetchIntervalSeconds`：隔多长时间抓取刷新一次缓存，默认30s；
2. `eureka.client.cacheRefreshExecutorExponentialBackOffBound`：缓存刷新请求超时时，下一次请求延迟的最大时间（即cacheRefreshExecutorExponentialBackOffBound * registryFetchIntervalSeconds）

### 3. 注册服务

前提开启了`eureka.client.register-with-eureka`这个配置值为true，否则不会发生注册动作，也不会有心跳任务和复制实例状态任务了。

Eureka提供了四个时机来注册服务实例。在DiscoveryClient构造函数期间，会启动三个定时服务，分别为：

1. 更新注册表任务；
2. 心跳任务；
3. 复制实例状态到服务端任务。

#### DiscoveryClient构造阶段

代码如下：

```java
// .... 省略代码
if (clientConfig.shouldRegisterWithEureka() && clientConfig.shouldEnforceRegistrationAtInit()) {
    try {
        if (!register() ) {
            // 注册失败
            throw new IllegalStateException("Registration error at startup. Invalid server response.");
        }
    } catch (Throwable th) {
        logger.error("Registration error at startup: {}", th.getMessage());
        throw new IllegalStateException(th);
    }
}
// ... 省略代码
```

即如果配置：`eureka.client.register-with-eureka`的值为true（默认为true）且`eureka.client.enforce-registration-at-init`的值为true（默认为false），则执行服务注册。

#### 心跳阶段

心跳任务的实现如下：

```java
private class HeartbeatThread implements Runnable {
    public void run() {
        if (renew()) {	// 执行续约
            // 续约成功，更新时间
            lastSuccessfulHeartbeatTimestamp = System.currentTimeMillis();
        }
    }
}

boolean renew() {
    EurekaHttpResponse<InstanceInfo> httpResponse;
    try {
        // 发送心跳
        // url（serviceUrl + "apps/" + appName + "/" + id）
        httpResponse = eurekaTransport.registrationClient.sendHeartBeat(instanceInfo.getAppName(), instanceInfo.getId(), instanceInfo, null);
        if (httpResponse.getStatusCode() == Status.NOT_FOUND.getStatusCode()) {
            // 没有在服务端上发现这个服务
            REREGISTER_COUNTER.increment();
            long timestamp = instanceInfo.setIsDirtyWithTime();
            boolean success = register();	// 执行服务注册动作
            if (success) {
                // 注册成功，设置服务的状态为not dirty
                instanceInfo.unsetIsDirty(timestamp);
            }
            return success; // 返回是否成功
        }
        // 返回心跳是否成功
        return httpResponse.getStatusCode() == Status.OK.getStatusCode();
    } catch (Throwable e) {
        logger.error(PREFIX + "{} - was unable to send heartbeat!", appPathIdentifier, e);
        return false;
    }
}
```

#### 复制状态阶段

InstanceInfoReplicator，负责将当前服务实例的状态更新到服务端上，其执行的任务如下：

```java
public void start(int initialDelayMs) {	// 在DiscoveryClient构造函数中启动
    if (started.compareAndSet(false, true)) {
        instanceInfo.setIsDirty();  // 设置为dirty的，以便注册
        // 定时调度
        Future next = scheduler.schedule(this, initialDelayMs, TimeUnit.SECONDS);
        scheduledPeriodicRef.set(next);
    }
}

public void run() {
    try {
        // 刷新实例信息
        // 如果实例的配置信息（address，lease等配置）都发生了改变，则会把instanceInfo设置为dirty
        discoveryClient.refreshInstanceInfo();

        // 获取dirtytime，在dirty的情况下会返回一个时间值，否则返回null
        Long dirtyTimestamp = instanceInfo.isDirtyWithTime();
        if (dirtyTimestamp != null) {
            // 不为null，说明dirty
            discoveryClient.register(); // 执行注册
            instanceInfo.unsetIsDirty(dirtyTimestamp);	// 重置为not dirty
        }
    } catch (Throwable t) {
        logger.warn("There was a problem with the instance info replicator", t);
    } finally {
        // 开启下一次定时调度
        Future next = scheduler.schedule(this, replicationIntervalSeconds, TimeUnit.SECONDS);
        scheduledPeriodicRef.set(next);
    }
}
```

#### AutoRegister阶段

在EurekaAutoServiceRegistration这个bean中（实现了SmartLifyCycle接口），当WebServer实例化事件发生之后或者是Bean生命周期的start阶段都会触发一次register操作，代码如下：

```java
@Override
public void start() {
    if (this.port.get() != 0) {
        if (this.registration.getNonSecurePort() == 0) {
            this.registration.setNonSecurePort(this.port.get());
        }

        if (this.registration.getSecurePort() == 0 && this.registration.isSecure()) {
            this.registration.setSecurePort(this.port.get());
        }
    }

    if (!this.running.get() && this.registration.getNonSecurePort() > 0) {
		// not running
        // 这里的注册是通过改变实例状态为UP这个事件来触发的，
        // 最终是调用的InstanceInfoReplicator的onDemandUpdate()方法来实现
        // 最终还是调用的InstanceInfoReplicator的run方法来决定是否要register的
        this.serviceRegistry.register(this.registration);

        this.context.publishEvent(new InstanceRegisteredEvent<>(this,
                                                                this.registration.getInstanceConfig()));
        this.running.set(true);	// 设置为running
    }
}

@Override
public void onApplicationEvent(ApplicationEvent event) {
    if (event instanceof WebServerInitializedEvent) {
        onApplicationEvent((WebServerInitializedEvent) event);
    }
    else if (event instanceof ContextClosedEvent) {
        onApplicationEvent((ContextClosedEvent) event);
    }
}

public void onApplicationEvent(WebServerInitializedEvent event) {
    String contextName = event.getApplicationContext().getServerNamespace();
    if (contextName == null || !contextName.equals("management")) {
        int localPort = event.getWebServer().getPort();
        if (this.port.get() == 0) {
            // 没有start过，调用start
            log.info("Updating port to " + localPort);
            this.port.compareAndSet(0, localPort);
            start();
        }
    }
}
```

既然注册动作会发生在这么多地方，那么注册动作到底发生在哪个阶段呢？一看配置，二看应用启动时长；大概率发生在AutoRegister这个阶段。

#### 注册实现

```java
boolean register() throws Throwable {
    EurekaHttpResponse<Void> httpResponse;
    try {
        // url（serviceUrl + "apps/" + appName）
        httpResponse = eurekaTransport.registrationClient.register(instanceInfo);
    } catch (Exception e) {
        logger.warn(PREFIX + "{} - registration failed {}", appPathIdentifier, e.getMessage(), e);
        throw e;
    }
    // 返回是否注册成功
    return httpResponse.getStatusCode() == Status.NO_CONTENT.getStatusCode();
}
```

### 4. 服务续约

即心跳，服务续约是通过发送心跳来实现的，心跳逻辑上面已经讲了，不再多说。涉及的配置有：

1. `eureka.instance.leaseRenewalIntervalInSeconds`：即多少秒发送一次心跳，默认是30s。
2. `eureka.client.heartbeatExecutorExponentialBackOffBound`：心跳请求超时之后，下一次心跳请求延迟的最大时间倍数（即heartbeatExecutorExponentialBackOffBound * leaseRenewalIntervalInSeconds为最大等待延迟时间），默认值为10，意味着如果多次心跳超时，下一次心跳请求的最大延迟时间不会超过10 * 30s；只要有一次心跳不超时，就会重置

### 5. 服务更新

服务实例配置发生了变化时，需要将本实例的状态信息同步到Server端。这个就是通过InstanceInfoReplicator来完成的，即在上面提到的复制状态阶段这个任务做的事情。首先会通过DiscoveryClient来刷新InstanceInfo的状态看是否dirty，如果是dirty，则需要重新register；否则不做任何操作。涉及的配置有：

1. `eureka.client.instanceInfoReplicationIntervalSeconds`：检测服务状态是否变化的间隔时长，默认为30s；
2. `eureka.client.initialInstanceInfoReplicationIntervalSeconds`：初始化InstanceInfoReplicator之后，延迟多长时间开始执行定时任务，默认40s。

### 6. 注销服务

服务注销发生在应用关闭或者说是CloudEurekaClient这个bean被销毁的时候。销毁的时候会执行它的shutdown方法，其实现如下：

```java
public synchronized void shutdown() {
    if (isShutdown.compareAndSet(false, true)) {
        logger.info("Shutting down DiscoveryClient ...");

        if (statusChangeListener != null && applicationInfoManager != null) {
            // 移除事件监听器
            applicationInfoManager.unregisterStatusChangeListener(statusChangeListener.getId());
        }

        cancelScheduledTasks();	// 关闭定时所有的定时任务

        // If APPINFO was registered
        if (applicationInfoManager != null
            && clientConfig.shouldRegisterWithEureka()
            && clientConfig.shouldUnregisterOnShutdown()) {
            applicationInfoManager.setInstanceStatus(InstanceStatus.DOWN);
            unregister();	// 注销服务
        }

        if (eurekaTransport != null) {
            eurekaTransport.shutdown();
        }

        heartbeatStalenessMonitor.shutdown();
        registryStalenessMonitor.shutdown();

        logger.info("Completed shut down of DiscoveryClient");
    }
}

void unregister() {
    if(eurekaTransport != null && eurekaTransport.registrationClient != null) {
        try {
            logger.info("Unregistering ...");
            // url（serviceUrl + "apps" + appName + "/" + id）
            EurekaHttpResponse<Void> httpResponse = eurekaTransport.registrationClient.cancel(instanceInfo.getAppName(), instanceInfo.getId());
            logger.info(PREFIX + "{} - deregister  status: {}", appPathIdentifier, httpResponse.getStatusCode());
        } catch (Exception e) {
            logger.error(PREFIX + "{} - de-registration failed{}", appPathIdentifier, e.getMessage(), e);
        }
    }
}
```

## 二、服务端

### 1. 启动原理

添加服务端依赖之后，我们需要在启动类上添加**@EnableEurekaServer**注解，这个注解会注册一个Marker类型的Bean，这个Bean没有实质作用，只是起到一个标记作用，要有这个bean相关的服务端bean才会加载。

在依赖包的META/spring.factories中有配置一个EurekaServerAutoConfiguration的配置类，其定义如下：

```java
@Configuration
@Import(EurekaServerInitializerConfiguration.class)
@ConditionalOnBean(EurekaServerMarkerConfiguration.Marker.class)
@EnableConfigurationProperties({ EurekaDashboardProperties.class,
		InstanceRegistryProperties.class })
@PropertySource("classpath:/eureka/server.properties")
public class EurekaServerAutoConfiguration extends WebMvcConfigurerAdapter {
}
```

从定义可以看出必须要有Marker这个bean才会加载这个配置类，所以Eureka的服务端启动是必须要添加**@EnableEurekaServer**注解的。

EurekaServer的实例化过程是通过EurekaServerInitializerConfiguration配置类来触发的，这个配置类实现了SmartLifecycle接口，意味着在spring启动完成后，会进入Bean的生命周期start阶段，即会调用这个配置类的start()方法，其实现如下：

```java
@Override
public void start() {
    new Thread(new Runnable() {
        @Override
        public void run() {
            try {
                // 调用EurekaServerBootstrap的初始化方法
                eurekaServerBootstrap.contextInitialized(
                    EurekaServerInitializerConfiguration.this.servletContext);
                log.info("Started Eureka Server");
				
                // 发布事件
                publish(new EurekaRegistryAvailableEvent(getEurekaServerConfig()));
                EurekaServerInitializerConfiguration.this.running = true;
                publish(new EurekaServerStartedEvent(getEurekaServerConfig()));
            }
            catch (Exception ex) {
                log.error("Could not initialize Eureka servlet context", ex);
            }
        }
    }).start();
}
```

所以Eureka Server启动的关键就是**EurekaServerBootstrap**，其初始化过程如下：

```java
public void contextInitialized(ServletContext context) {
    try {
        initEurekaEnvironment();	// 初始化Eureka环境，不重要
        initEurekaServerContext();	// 初始化上下文，重要

        context.setAttribute(EurekaServerContext.class.getName(), this.serverContext);
    }
    catch (Throwable e) {
        log.error("Cannot bootstrap eureka server :", e);
        throw new RuntimeException("Cannot bootstrap eureka server :", e);
    }
}

protected void initEurekaServerContext() throws Exception {
    // 序列化器
    JsonXStream.getInstance().registerConverter(new V1AwareInstanceInfoConverter(),
                                                XStream.PRIORITY_VERY_HIGH);
    XmlXStream.getInstance().registerConverter(new V1AwareInstanceInfoConverter(),
                                               XStream.PRIORITY_VERY_HIGH);

    //.... 省略代码

    EurekaServerContextHolder.initialize(this.serverContext);
    // 从其他节点拷贝服务注册信息表，存储在本地
    int registryCount = this.registry.syncUp();
    // 打开此节点，开始提供注册表服务（即可以从此节点获取服务信息了）
    // 注意这个只影响获取服务信息的接口，不会影响服务注册、更新等接口
    this.registry.openForTraffic(this.applicationInfoManager, registryCount);
}
```

### 2. Eureka Server上下文初始化

Eureka Server的上下文主要包括：

1. 当前节点所处集群的节点信息；
2. 当前节点管理的服务注册表。

上下文初始化是有EurekaServerContext来完成的，其定义包含在EurekaServerAutoConfiguration配置类中，定义如下：

```java
@Bean
public EurekaServerContext eurekaServerContext(ServerCodecs serverCodecs,
                                               PeerAwareInstanceRegistry registry, PeerEurekaNodes peerEurekaNodes) {
    return new DefaultEurekaServerContext(this.eurekaServerConfig, serverCodecs,
                                          registry, peerEurekaNodes, this.applicationInfoManager);
}
```

其实现如下：

```java
@Inject
public DefaultEurekaServerContext(EurekaServerConfig serverConfig,
                                  ServerCodecs serverCodecs,
                                  PeerAwareInstanceRegistry registry,
                                  PeerEurekaNodes peerEurekaNodes,
                                  ApplicationInfoManager applicationInfoManager) {
    this.serverConfig = serverConfig;
    this.serverCodecs = serverCodecs;
    this.registry = registry;
    this.peerEurekaNodes = peerEurekaNodes;
    this.applicationInfoManager = applicationInfoManager;
}

@PostConstruct
@Override
public void initialize() {
    // 初始化
    peerEurekaNodes.start();	// 处理集群节点
    try {
        registry.init(peerEurekaNodes); // 初始化注册表情况
    } catch (Exception e) {
        throw new RuntimeException(e);
    }
    logger.info("Initialized");
}
```

#### 集群节点管理

集群节点的管理是通过**PeerEurekaNodes**来实现的，其不会包含Eureka Server的所有节点，只包含了配置在serviceUrl中对应的节点。这个类的加载是在EurekaServerAutoConfiguration配置类中配置的，代码如下：

```java
@Bean
@ConditionalOnMissingBean
public PeerEurekaNodes peerEurekaNodes(PeerAwareInstanceRegistry registry,
                                       ServerCodecs serverCodecs) {
    return new RefreshablePeerEurekaNodes(registry, this.eurekaServerConfig,
                                          this.eurekaClientConfig, serverCodecs, this.applicationInfoManager);
}
```

从配置可以看出来，其使用的是子类RefreshablePeerEurekaNodes，为什么使用这个子类呢？原因是：方便Eureka集群节点的动态扩展，这个子类会监听环境配置的变更，当监听到serviceUrl配置的变化时，会更新PeerEurekaNodes中的节点，从而可以做到动态的增加或者是删除节点，从而实现Eureka集群节点的动态扩展。

先看一下父类**PeerEurekaNodes**对节点的管理实现逻辑：

```java
public void start() {
    // 创建一个执行器
    taskExecutor = Executors.newSingleThreadScheduledExecutor(
        new ThreadFactory() {
            @Override
            public Thread newThread(Runnable r) {
                Thread thread = new Thread(r, "Eureka-PeerNodesUpdater");
                thread.setDaemon(true);
                return thread;
            }
        }
    );
    try {
        // 首先解析serviceUrl配置，然后根据这个serviceUrl配置来更新管理的PeerEurekaNode
        // 会根据serviceUrl来创建对应的PeerEurekaNode实例来表示集群节点
        // 从这里就可以看出，如果serviceUrl不变化，那么当前节点管理的集群节点就不会发生变化
        updatePeerEurekaNodes(resolvePeerUrls());
        Runnable peersUpdateTask = new Runnable() {
            @Override
            public void run() {
                try {
                    updatePeerEurekaNodes(resolvePeerUrls());
                } catch (Throwable e) {
                    logger.error("Cannot update the replica Nodes", e);
                }

            }
        };
        // 定时更新集群节点信息
        taskExecutor.scheduleWithFixedDelay(
            peersUpdateTask,
            serverConfig.getPeerEurekaNodesUpdateIntervalMs(),
            serverConfig.getPeerEurekaNodesUpdateIntervalMs(),
            TimeUnit.MILLISECONDS
        );
    } catch (Exception e) {
        throw new IllegalStateException(e);
    }
    for (PeerEurekaNode node : peerEurekaNodes) {
        logger.info("Replica node URL:  {}", node.getServiceUrl());
    }
}
```

而子类RefreshPeerEurekaNodes实现了监听环境处理如下：

```java
@Override
public void onApplicationEvent(final EnvironmentChangeEvent event) {
    // EnvironmentChangeEvent 环境改变事件，即配置发生了变化，所以在引入动态配置中心的情况下可以实现
    // Eureka集群的动态扩展
    if (shouldUpdate(event.getKeys())) { // 判断是否需要update
        updatePeerEurekaNodes(resolvePeerUrls());
    }
}

protected boolean shouldUpdate(final Set<String> changedKeys) {
    assert changedKeys != null;

    if (clientConfig.shouldUseDnsForFetchingServiceUrls()) {
        return false;
    }

    if (changedKeys.contains("eureka.client.region")) {
        return true;
    }

    for (final String key : changedKeys) {
        // serviceUrl和availabilityZones配置发生了变化，就需要update了
        if (key.startsWith("eureka.client.service-url.")
            || key.startsWith("eureka.client.availability-zones.")) {
            return true;
        }
    }
    return false;
}
```

#### 注册表初始化

注册表实例定义：

```java
@Bean
public PeerAwareInstanceRegistry peerAwareInstanceRegistry(
    ServerCodecs serverCodecs) {
    this.eurekaClient.getApplications(); // force initialization
    return new InstanceRegistry(this.eurekaServerConfig, this.eurekaClientConfig,
                                serverCodecs, this.eurekaClient,
                                this.instanceRegistryProperties.getExpectedNumberOfClientsSendingRenews(),
                                this.instanceRegistryProperties.getDefaultOpenForTrafficCount());
}
```

从上面的定义就可以看出来，在创建InstanceRegistry的时候，会先强制EurekaClient（每个Eureka集群中的节点也是彼此之间的客户端，所以每个Server节点都会包含一个EurekaClient）初始化（即先执行Eureka客户端的逻辑）。**InstanceRegistry**是服务端的核心类（InstanceRegistry是spring-cloud-netflix提供的类，最终会委托给netflix的PeerAwareInstanceRegistryImpl类来完成），所有客户端的注册、获取服务注册表、注销、状态更新等操作都是由这个类来完成的。我们先看一下它的init()方法做了什么：

```java
@Override
public void init(PeerEurekaNodes peerEurekaNodes) throws Exception {
    this.numberOfReplicationsLastMin.start();
    this.peerEurekaNodes = peerEurekaNodes;
    initializedResponseCache();	// 初始化responseCache，负责存储注册的实例的payload
    scheduleRenewalThresholdUpdateTask();	// 心跳数量统计任务，用于自我保护
    initRemoteRegionRegistry();	// 基本没用，可以不用关注，远程region注册表相关的任务

    try {
        Monitors.registerObject(this);
    } catch (Throwable e) {
        logger.warn("Cannot register the JMX monitor for the InstanceRegistry :", e);
    }
}
```

### 3. 注册表拷贝

集群节点重启或者是新节点加入时，从其他节点拷贝服务注册信息，发生时机上文有提及，即EurekaServerBootstrap.initEurekaServerContext()方法中，通过InstanceRegistry的**syncUp**方法来实现，其实现如下：

```java
@Override
public int syncUp() {
    int count = 0;

    for (int i = 0; ((i < serverConfig.getRegistrySyncRetries()) && (count == 0)); i++) {
        // 在重试次数范围内，且没用从其他节点获取到服务注册信息，则继续
        if (i > 0) {
            try {
                // 重试时，先睡眠等待一段时间，等集群中的其他节点启动起来或者是等有服务注册到集群的其他节点上
                Thread.sleep(serverConfig.getRegistrySyncRetryWaitMs());
            } catch (InterruptedException e) {
                logger.warn("Interrupted during registry transfer..");
                break;
            }
        }
        // eureka客户端获取的服务注册表
        // 上文说了eureka集群的每个节点也是客户端，是客户端就会从集群其他节点获取服务注册信息
        // 即通过eureka.client.serviceUrl配置的列表节点获取服务注册信息
        Applications apps = eurekaClient.getApplications();
        for (Application app : apps.getRegisteredApplications()) {
            for (InstanceInfo instance : app.getInstances()) {
                try {
                    if (isRegisterable(instance)) {
                        // 将其他节点获取的服务注册信息在本节点上执行一个注册，即拷贝到本节点
                        register(instance, instance.getLeaseInfo().getDurationInSecs(), true);
                        count++;
                    }
                } catch (Throwable t) {
                    logger.error("During DS init copy", t);
                }
            }
        }
    }
    return count;
}
```

相关配置：

1. `eureka.server.registrySyncRetries`：同步其他节点信息重试次数，默认5次；
2. `eureka.server.registrySyncRetryWaitMs`：重试时，等待的间隔时间，默认30s；

:warning:这里会存在一致性的问题。举例：集群三个节点Node1、Node2、Node3，有三个服务A、B、C，其中A注册到了Node1，B和C注册到了Node2，而Node3配置的serviceUrl是只有Node1的路径，那么Node3启动时只拷贝到服务A的注册信息，Node2上的服务注册信息是什么时候同步过来的呢？

### 4. 剔除机制

剔除：服务端在指定时间段内都没有收到某个客户端节点的心跳消息，就会将这个服务从注册表中删除。这个剔除动作是通过定时任务来实现。

定时任务的开启是由方法EurekaServerBootstrap.initEurekaServerContext()中调用的InstanceRegistry.openForTraffic()来触发的（会调用postInit()方法来触发定时任务）：

```java
protected void postInit() {
    renewsLastMin.start();
    if (evictionTaskRef.get() != null) {
        evictionTaskRef.get().cancel();
    }
    evictionTaskRef.set(new EvictionTask());	
    // Eviction剔除任务的开启
    evictionTimer.schedule(evictionTaskRef.get(),
                           serverConfig.getEvictionIntervalTimerInMs(),
                           serverConfig.getEvictionIntervalTimerInMs());
}
```

剔除任务做的事情如下：

```java
@Override
public void run() {
    try {
        long compensationTimeMs = getCompensationTimeMs();// 计算一个补偿时间
        evict(compensationTimeMs);	// 执行剔除动作
    } catch (Throwable e) {
        logger.error("Could not run the evict task", e);
    }
}

public void evict(long additionalLeaseMs) {

    if (!isLeaseExpirationEnabled()) {
        // 租约过期剔除是否enabled
        return;
    }
    // 1. 没有开启自我保护机制，走这里
    // 2. 开启了自我保护机制，但是最后一分钟收到的心跳数超过了预期值，也走这里

    List<Lease<InstanceInfo>> expiredLeases = new ArrayList<>();
    // 遍历本地注册表
    for (Entry<String, Map<String, Lease<InstanceInfo>>> groupEntry : registry.entrySet()) {
        Map<String, Lease<InstanceInfo>> leaseMap = groupEntry.getValue();
        if (leaseMap != null) {
            for (Entry<String, Lease<InstanceInfo>> leaseEntry : leaseMap.entrySet()) {
                Lease<InstanceInfo> lease = leaseEntry.getValue();
                if (lease.isExpired(additionalLeaseMs) && lease.getHolder() != null) {
                    // 租约过期了，加入到过期列表中
                    expiredLeases.add(lease);
                }
            }
        }
    }

    int registrySize = (int) getLocalRegistrySize(); // 本地所有服务实例的个数（过期不过期都算进来）
    // 计算一个注册表中的上限
    int registrySizeThreshold = (int) (registrySize * serverConfig.getRenewalPercentThreshold());
    int evictionLimit = registrySize - registrySizeThreshold;	// 可删除的个数
	// 取一个小值，意味着即使有很多的实例过期了，但由于上限的存在可能不会一次将所有过期的实例剔除
  	// 如果过期的小于限制，则一次性剔除所有过期的
    // 如果过期的大于限制，则一次只剔除限制的，剩下的在下一个轮回进行剔除
    int toEvict = Math.min(expiredLeases.size(), evictionLimit);
    if (toEvict > 0) {

        Random random = new Random(System.currentTimeMillis());
        for (int i = 0; i < toEvict; i++) {
            // Pick a random item (Knuth shuffle algorithm)
            // 取一个随机项
            int next = i + random.nextInt(expiredLeases.size() - i);
            // 交换
            Collections.swap(expiredLeases, i, next);
            // 获取随机项对应的实例
            Lease<InstanceInfo> lease = expiredLeases.get(i);

            String appName = lease.getHolder().getAppName();
            String id = lease.getHolder().getId();
            internalCancel(appName, id, false);	// 执行节点内部移除（意味着这个移除操作不会拷贝到集群中的其他节点上去，只是一个本地行为）
        }
    }
}
```

相关配置：

1. `eureka.server.evictionIntervalTimerInMs`：执行evit任务的间隔，默认60s；

2. `eureka.instance.leaseExpirationDurationInSeconds`：租约过期多久算是expire，默认是90s（如果客户端没有提供这个配置，那么服务端默认会将这个时间设置为90s），意味着如果服务端90s内没有收到心跳消息，会认为这个实例已经失效了。但是注意，eureka中对这个过期是存在bug的，它会在2倍这个时间（即180s）才会任务实例失效；相关代码如下：

   ```java
   public Lease(T r, int durationInSecs) {
       holder = r;
       registrationTimestamp = System.currentTimeMillis();
       lastUpdateTimestamp = registrationTimestamp;
       duration = (durationInSecs * 1000);	// 创建租约时，计算duration值（由注册时上传）
   }
   
   public void renew() {
       // 心跳时，更新时间，这里加了一个duration
       lastUpdateTimestamp = System.currentTimeMillis() + duration;
   }
   
   /**
    * Due to possible wide ranging impact to existing usage, this will
    * not be fixed.  这个bug不会修复*/
   public boolean isExpired(long additionalLeaseMs) {
       // 这里判断过期的时候，又加了一个duration，也就是加了2*duration
       return (evictionTimestamp > 0 || System.currentTimeMillis() > (lastUpdateTimestamp + duration + additionalLeaseMs));
   }
   ```

### 5. 自我保护机制

什么是自我保护机制？自我保护机制是指当Eureka Server在特定时间段内收到的心跳请求低于某个阈值时，不会再执行剔除操作（意味着即使某些节点长时间没有发送心跳过来也不会从注册表中删除）。

自我保护机制从上面的剔除机制就可以看出，在剔除时会保证一个注册表中的实例上限数，不会把所有的过期节点都删除。

在上面的剔除操作中还有一个isLeaseExpirationEnabled()方法，我们来看一下：

```java
@Override
public boolean isLeaseExpirationEnabled() {
    if (!isSelfPreservationModeEnabled()) {
        // 没有开启自我保护机制，直接返回true
        return true;
    }
    // 开启了自我保护机制，且最后一分钟收到的心跳数超过了预期值，则返回true，否则返回false（返回false就不会执行expire删除了）
    return numberOfRenewsPerMinThreshold > 0 && getNumOfRenewsInLastMin() > numberOfRenewsPerMinThreshold;
}
```

numberOfRenewsPerMinThreshold会定时刷新，刷新任务在上面提到的init()方法中的scheduleRenewalThresholdUpdateTask()方法触发，代码如下：

```java
private void scheduleRenewalThresholdUpdateTask() {
    timer.schedule(new TimerTask() {
        @Override
        public void run() {
            updateRenewalThreshold();
        }
    }, serverConfig.getRenewalThresholdUpdateIntervalMs(),
                   serverConfig.getRenewalThresholdUpdateIntervalMs());
}

private void updateRenewalThreshold() {
    try {
        // 通过eurekaClient获取的注册表
        Applications apps = eurekaClient.getApplications();
        int count = 0;
        // 统计出注册的实例数量（UP状态的）
        for (Application app : apps.getRegisteredApplications()) {
            for (InstanceInfo instance : app.getInstances()) {
                if (this.isRegisterable(instance)) {
                    ++count;
                }
            }
        }
        synchronized (lock) {
            // 如果实例数量在期望的阈值以上，或者是自我保护模式关闭了
            // 则更新期望一分钟内应该收到的心跳数量
            if ((count) > (serverConfig.getRenewalPercentThreshold() * expectedNumberOfClientsSendingRenews)
                || (!this.isSelfPreservationModeEnabled())) {
                // 将期望收到的心跳数量更新为UP状态的实例数
                this.expectedNumberOfClientsSendingRenews = count;
                updateRenewsPerMinThreshold();
            }
        }
    } catch (Throwable e) {
        logger.error("Cannot update renewal threshold", e);
    }
}

protected void updateRenewsPerMinThreshold() {
    // 重新计算期望一分钟内应该受到的心跳数量，并更新
    // 期望一分钟内收到的心跳消息下限=[期望一分钟内收到的心跳=期望收到的心跳数量*（60/期望客户端隔多少秒发送一次心跳）]*限制占比
    this.numberOfRenewsPerMinThreshold = (int) (this.expectedNumberOfClientsSendingRenews
                                                * (60.0 / serverConfig.getExpectedClientRenewalIntervalSeconds())
                                                * serverConfig.getRenewalPercentThreshold());
}
```

这里在统计注册服务的实例数量的时候并不是直接用的本节点上的注册表，而是从集群其他节点上获取的注册表来统计的实例数量；当这个实例数量比预期应该存活的实例更多时，我们需要更新期望收到的心跳数量；而如果这个实例数量已经比预期应该存活的实例少时，不再更新期望收到的心跳数量，从而也就会促使条件`getNumOfRenewsInLastMin() > numberOfRenewsPerMinThreshold`为false，也就避免了下线更多的实例（即自我保护机制）。

相关配置：

1. `eureka.server.renewalThresholdUpdateIntervalMs`：刷新numberOfRenewsPerMinThreshold任务的间隔时间，默认15分钟；即每隔15分钟会更新一次节点在一分钟内应该收到的心跳消息数；
2. `eureka.server.renewalPercentThreshold`：心跳消息占用期望收到心跳消息的百分比，默认0.85；
3. `eureka.server.expectedClientRenewalIntervalSeconds`：期望客户端隔多少秒发送一次心跳，默认30s，意味着期望客户端每30秒发送一次心跳过来。

### 6. 服务提供

服务端需要提供实例注册、单个实例信息获取、实例注册表获取、实例状态更新等接口服务，由客户端调用这些接口来完成服务注册等相关功能。在Eureka Server中这些是通过Jersey框架来完成的，在Server端会开启一个Jersey服务，由Jersey提供的服务接口来完成相关服务的注册、更新等操作。在EurekaServerAutoConfiguration中配置了一个Application和一个Filter，就是由这两个来完成注册中心相关功能的，具体我们看一下：

```java
// 定义一个Filter，这个filter会过滤所有/eureka/开头的请求，由ServletContainer去处理
// ServletContainer内部包装了一个Jersey提供的Application实现，由它去完成/eureka/相关的请求
@Bean
public FilterRegistrationBean jerseyFilterRegistration(
    javax.ws.rs.core.Application eurekaJerseyApp) {
    FilterRegistrationBean bean = new FilterRegistrationBean();
    bean.setFilter(new ServletContainer(eurekaJerseyApp));
    bean.setOrder(Ordered.LOWEST_PRECEDENCE);
    bean.setUrlPatterns(
        Collections.singletonList(EurekaConstants.DEFAULT_PREFIX + "/*"));

    return bean;
}

// Jersey应用的定义
@Bean
public javax.ws.rs.core.Application jerseyApplication(Environment environment,
                                                      ResourceLoader resourceLoader) {
	// 定义了一个类路径扫描器
    ClassPathScanningCandidateComponentProvider provider = new ClassPathScanningCandidateComponentProvider(
        false, environment);

    // 添加过滤器，过滤由Path和Provider注解的类
    provider.addIncludeFilter(new AnnotationTypeFilter(Path.class));
    provider.addIncludeFilter(new AnnotationTypeFilter(Provider.class));

    // 扫描com.netflix.eureka和com.netflix.discovery这两个包
    // 即扫描这两个包下由@Path和@Provider注解的类
    Set<Class<?>> classes = new HashSet<>();
    for (String basePackage : EUREKA_PACKAGES) {
        Set<BeanDefinition> beans = provider.findCandidateComponents(basePackage);
        for (BeanDefinition bd : beans) {
            Class<?> cls = ClassUtils.resolveClassName(bd.getBeanClassName(),
                                                       resourceLoader.getClassLoader());
            classes.add(cls);
        }
    }

    // Construct the Jersey ResourceConfig
    Map<String, Object> propsAndFeatures = new HashMap<>();
    propsAndFeatures.put(
        // Skip static content used by the webapp
        ServletContainer.PROPERTY_WEB_PAGE_CONTENT_REGEX,
        EurekaConstants.DEFAULT_PREFIX + "/(fonts|images|css|js)/.*");
	// 利用扫描出来的类，创建一个资源池，请求的处理就是由这些扫描出来的类处理的
    DefaultResourceConfig rc = new DefaultResourceConfig(classes);
    rc.setPropertiesAndFeatures(propsAndFeatures);

    return rc;
}
```

下面以服务注册为例，对服务处理流程进行介绍。在com.netflix.eureka包下提供了一个**ApplicationResource**资源类负责实例相关的操作，其内包含了实例注册的接口逻辑，实现如下：

```java
@POST
@Consumes({"application/json", "application/xml"})
public Response addInstance(InstanceInfo info,
                            @HeaderParam(PeerEurekaNode.HEADER_REPLICATION) String isReplication) {
    logger.debug("Registering instance {} (replication={})", info.getId(), isReplication);
    if (isBlank(info.getId())) {
        return Response.status(400).entity("Missing instanceId").build();
    } else if (isBlank(info.getHostName())) {
        return Response.status(400).entity("Missing hostname").build();
    } else if (isBlank(info.getIPAddr())) {
        return Response.status(400).entity("Missing ip address").build();
    } else if (isBlank(info.getAppName())) {
        return Response.status(400).entity("Missing appName").build();
    } else if (!appName.equals(info.getAppName())) {
        return Response.status(400).entity("Mismatched appName, expecting " + appName + " but was " + info.getAppName()).build();
    } else if (info.getDataCenterInfo() == null) {
        return Response.status(400).entity("Missing dataCenterInfo").build();
    } else if (info.getDataCenterInfo().getName() == null) {
        return Response.status(400).entity("Missing dataCenterInfo Name").build();
    }

    // handle cases where clients may be registering with bad DataCenterInfo with missing data
    DataCenterInfo dataCenterInfo = info.getDataCenterInfo();
    if (dataCenterInfo instanceof UniqueIdentifier) {
        String dataCenterInfoId = ((UniqueIdentifier) dataCenterInfo).getId();
        if (isBlank(dataCenterInfoId)) {
            boolean experimental = "true".equalsIgnoreCase(serverConfig.getExperimental("registration.validation.dataCenterInfoId"));
            if (experimental) {
                String entity = "DataCenterInfo of type " + dataCenterInfo.getClass() + " must contain a valid id";
                return Response.status(400).entity(entity).build();
            } else if (dataCenterInfo instanceof AmazonInfo) {
                AmazonInfo amazonInfo = (AmazonInfo) dataCenterInfo;
                String effectiveId = amazonInfo.get(AmazonInfo.MetaDataKey.instanceId);
                if (effectiveId == null) {
                    amazonInfo.getMetadata().put(AmazonInfo.MetaDataKey.instanceId.getName(), info.getId());
                }
            } else {
                logger.warn("Registering DataCenterInfo of type {} without an appropriate id", dataCenterInfo.getClass());
            }
        }
    }
	// 通过Registry注册一个实例
    // registry是PeerAwareInstanceRegistry类型实例（对应InstanceRegistry）
    registry.register(info, "true".equals(isReplication));
    return Response.status(204).build();  // 204 to be backwards compatible
}
```

接口层会调用InstanceRegistry来完成相关操作，在InstanceRegistry中的注册接口实现如下：

```java
@Override
public void register(final InstanceInfo info, final boolean isReplication) {
    handleRegistration(info, resolveInstanceLeaseDuration(info), isReplication); // 发布实例注册事件（spring里面的事件）
    super.register(info, isReplication);
}

// super.register()
// 即PeerAwareInstanceRegistryImpl中的实现
@Override
public void register(final InstanceInfo info, final boolean isReplication) {
    int leaseDuration = Lease.DEFAULT_DURATION_IN_SECS; // 初始化duration，90s
    if (info.getLeaseInfo() != null && info.getLeaseInfo().getDurationInSecs() > 0) {
        leaseDuration = info.getLeaseInfo().getDurationInSecs();
    }
    super.register(info, leaseDuration, isReplication);	// 继续调用父类注册方法
    // 同步到集群中的其他节点，就是通过PeerEurekaNodes管理的集群节点来发送的同步请求
    replicateToPeers(Action.Register, info.getAppName(), info.getId(), info, null, isReplication);
}
```

因此服务提供的逻辑如下图：

![image-20201015142721094](E:\study\资料文档\笔记\spring\images\image-20201015142721094.png)

集群同步的时候，只会将操作复制到与当前节点直连的节点上去，非直连节点不会进行拷贝。集群间节点通信机制Eureka也是使用的Reactor模式，感兴趣的可以去看看它的实现（具体实现在PeerEurekaNode类中）。

### 7. 最终一致性

从上面我们也知道，在服务端集群中，集群各节点之间在某些情况下会存在不一致的情况，比如说：

1. 在集群各节点启动时，由于各客户端连接的节点不一样，会存在不一致的情况；
2. 在集群某节点由于网络的问题，接收不到其他节点发送过来的心跳消息，导致的节点本身evit某个实例，从而导致的和其他节点不一致；
3. 其他情况。

这种不一致情况的发生，会导致客户端获取到的服务注册表信息可能不一样，从而可能造成服务间调用的问题。那么在Eureka集群中是如何解决这种不一致问题呢？Eureka提供的是AP服务，首先保证的是可用性问题，提供最终一致性服务。Eureka就是通过Replication来完成的最终一致性服务。

比如说，集群某个节点由于网络问题，导致无法收到心跳消息，从而导致从节点的列表中将实例删除了；当网络恢复，可以收到replication的心跳消息之后，被删除的实例会再次添加到列中，从而达到和其他节点的信息一致。