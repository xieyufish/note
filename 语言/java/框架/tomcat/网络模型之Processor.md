# Tomcat网络模型之Processor

在上篇文章中介绍到SocketProcessor和Handler连接处理器的时候，我们说到Handler连接处理器最终的处理请求的逻辑是通过Processor实例的***process()***方法来完成。Processor是一个应用协议处理器接口，主要职责是负责读取Socket中的内容，并负责将这些内容按照不同的应用协议格式进行解析。在Tomcat8.5版本中，主要支持三种不同应用协议处理：

1. **Http11Processor：**Http/1.1协议处理器；
2. **StreamProcessor：**Http/2协议处理器；
3. **AjpProcessor：**Ajp协议处理器。

本文将解析我们最熟悉的Http/1.1协议处理流程，来了解Tomcat处理应用请求的一个流程。

## 一、应用协议处理器入口

从上篇文章我们知道，连接处理器Handler是通过***Processor.process()***方法来完成请求处理的，从而可以其就是应用协议处理器的入口。Tomcat中在**Processor**接口的轻实现类**AbstractProcessorLight**中提供了***process()***方法的模板实现，其代码如下：

```java
/**
 * socketWrapper: 发生IO事件的socket封装对象
 * status：发生的IO事件类型（封装过的）
 */
@Override
public SocketState process(SocketWrapperBase<?> socketWrapper, SocketEvent status)
    throws IOException {

    SocketState state = SocketState.CLOSED;	// 初始化Socket状态为CLOSED
    Iterator<DispatchType> dispatches = null;	// 初始化dispatch（作用是什么呢？）
    do {	// 循环处理
        if (dispatches != null) {
            DispatchType nextDispatch = dispatches.next();
            state = dispatch(nextDispatch.getSocketStatus());
            if (!dispatches.hasNext()) {
                state = checkForPipelinedData(state, socketWrapper);
            }
        } else if (status == SocketEvent.DISCONNECT) {
            // 如果IO事件类型是要断开连接，则什么都不做，SocketState值为CLOSED，有调用方去关闭连接
        } else if (isAsync() || isUpgrade() || state == SocketState.ASYNC_END) {
            // 如果是异步请求、协议升级协商或者是异步请求完成
            state = dispatch(status);
            state = checkForPipelinedData(state, socketWrapper);
        } else if (status == SocketEvent.OPEN_WRITE) {
            // 如果当前的IO事件是写事件，则返回LONG状态，在连接处理器中会注册一个感兴趣IO读事件
            state = SocketState.LONG;
        } else if (status == SocketEvent.OPEN_READ) {
            // 如果当前的IO事件是读事件，则调用service()方法来处理请求
            state = service(socketWrapper);
        } else if (status == SocketEvent.CONNECT_FAIL) {
            // 如果当前的IO事件是连接失败（比如TLS握手阶段失败了），则记录日志，并设置响应状态为400
            logAccess(socketWrapper);
        } else {
            // 其他情况下，都是关闭socket
            state = SocketState.CLOSED;
        }

        if (isAsync()) {
            // 如果是异步请求，则预处理请求
            state = asyncPostProcess();
        }

        if (dispatches == null || !dispatches.hasNext()) {
            // 获取dispatches数据
            dispatches = getIteratorAndClearDispatches();
        }
        
        // 如果state状态是CLOSED状态则结束此次请求处理流程
        // 如果dispatches为空，且state状态不为ASYNC_END状态，则结束此次请求处理流程
    } while (state == SocketState.ASYNC_END ||
             dispatches != null && state != SocketState.CLOSED);

    return state;	// 返回socket的下一步状态
}
```

***process()***处理方法最终返回的结果为**SocketState**类型，这个返回值会告诉连接处理器Handler实例socket连接下一步应该处于什么状态，从而决定处理socket连接。

## 二、请求处理核心逻辑

从上述***process()***方法我们可以知道，处理请求的核心逻辑在于***service()***方法中的实现，我将以Http/1.1协议对应的实现类**Http11Processor**为例进行解析；在上篇文章中，我们说连接处理器会从processorCache实例池中取出一个Processor实例与当前连接绑定，如果实例池中无可用的实例会创建一个新的Processor实例，我们就看一下**Http11Processor**的创建代码，如下：

```java
/**
 * 这段代码再Http11的协议类中：AbstractHttp11Protocol.java
 */
@Override
protected Processor createProcessor() {
    // 根据当前协议实例和Endpoint实例构建一个协议处理器
    Http11Processor processor = new Http11Processor(this, getEndpoint());
    // 设置协议处理器的CoyoteAdapter，通过它可以访问到Servlet容器
    // 从而实现Connector与Container两大组件的关联
    processor.setAdapter(getAdapter());
    processor.setMaxKeepAliveRequests(getMaxKeepAliveRequests());	// 控制最大存活连接数，默认值为100
    processor.setConnectionUploadTimeout(getConnectionUploadTimeout());	// 控制请求数据上传的最大超时时间，默认5分钟
    processor.setDisableUploadTimeout(getDisableUploadTimeout());	// 控制是否开启数据上传超时功能，默认不开启
    processor.setRestrictedUserAgents(getRestrictedUserAgents());	// 限制用户代理类型的正则表达式
    processor.setMaxSavePostSize(getMaxSavePostSize());	// 用于控制POST方式下FORM和CLIENT-CERT认证方式时的最大缓存数据量，默认4KB
    return processor;
}
```

在构造Http11Processor处理器期间，会分配很多资源用于接受和保存当前请求的相关信息，其构造方法实现如下：

```java
public Http11Processor(AbstractHttp11Protocol<?> protocol, AbstractEndpoint<?> endpoint) {
    super(endpoint);	// 调用父类构造方法
    this.protocol = protocol;	// 记录协议类实例
	
    // 构造一个Http协议解析器
    httpParser = new HttpParser(protocol.getRelaxedPathChars(),	// 请求路劲中某些特殊字符是需要%nn编码的，此属性指定允许请求路径中包含哪些特殊字符，默认为null
                                protocol.getRelaxedQueryChars());	// 路劲查询参数中允许哪些特殊字符，默认为null

    // 请求输入缓存，用于保存请求数据
    // 默认允许的最大Http请求头大小为8kb，默认不拒绝非法的请求头（即对包含非法请求头的请求不会返回400）
    inputBuffer = new Http11InputBuffer(request, protocol.getMaxHttpHeaderSize(),
                                        protocol.getRejectIllegalHeader(), httpParser);
    request.setInputBuffer(inputBuffer); 	// 与请求对象关联起来

    // 响应缓存
    outputBuffer = new Http11OutputBuffer(response, protocol.getMaxHttpHeaderSize(),
                                          protocol.getSendReasonPhrase());
    response.setOutputBuffer(outputBuffer); // 与响应对象关联起来

    // 创建并添加Identity过滤器
    inputBuffer.addFilter(new IdentityInputFilter(protocol.getMaxSwallowSize()));
    outputBuffer.addFilter(new IdentityOutputFilter());

    // 创建并添加Chunked过滤器
    inputBuffer.addFilter(new ChunkedInputFilter(protocol.getMaxTrailerSize(),
                                                 protocol.getAllowedTrailerHeadersInternal(), protocol.getMaxExtensionSize(),
                                                 protocol.getMaxSwallowSize()));
    outputBuffer.addFilter(new ChunkedOutputFilter());

    inputBuffer.addFilter(new VoidInputFilter());
    outputBuffer.addFilter(new VoidOutputFilter());
	// 缓存过滤器
    inputBuffer.addFilter(new BufferedInputFilter());
	// 压缩过滤器
    outputBuffer.addFilter(new GzipOutputFilter());

    pluggableFilterIndex = inputBuffer.getFilters().length;
}

/////// 抽象父类 AbstractProcessor.java中的构造方法
public AbstractProcessor(AbstractEndpoint<?> endpoint) {
    // 创建一个新的请求和响应对象（注意，是coyote包下的请求和响应对象）
    this(endpoint, new Request(), new Response());
}

// 父类构造方法
protected AbstractProcessor(AbstractEndpoint<?> endpoint, Request coyoteRequest,
                            Response coyoteResponse) {
    this.endpoint = endpoint;
    asyncStateMachine = new AsyncStateMachine(this);	// 异步状态机，存放当前的异步请求状态，默认值是非异步的
    request = coyoteRequest;
    response = coyoteResponse;
    response.setHook(this);	// 设置动作钩子实例（ActionMode，AbstractProcessor实现了这个接口）
    request.setResponse(response);	// 请求与响应关联
    request.setHook(this);
    userDataHelper = new UserDataHelper(getLog());	// 日志处理帮助类
}
```

下面，我们看一下在**Http11Processor**实现类中是如何处理一个请求的：

```java
/**
 * socketWrapper: 代表了请求的socket对象
 */
@Override
public SocketState service(SocketWrapperBase<?> socketWrapper)
    throws IOException {
    RequestInfo rp = request.getRequestProcessor();	// 获取存储当前请求信息的实例
    rp.setStage(org.apache.coyote.Constants.STAGE_PARSE);	// 记录请求处理的阶段

    // 设置socket对象
    // 因为Processor对象是可以复用的，所以在每次处理具体的请求之前，都需要重新绑定一个代表请求的socket
    setSocketWrapper(socketWrapper);

    // 标志
    keepAlive = true;	// 默认keep-alive请求
    openSocket = false;	// 处理完请求之后，是否继续开启socket
    readComplete = true;	// 表示是否读取了一个完整的Http请求数据
    boolean keptAlive = false;	// 表示在没有读取到这个请求的任何数据时，是否继续keepAlive
    SendfileState sendfileState = SendfileState.DONE;

    while (!getErrorState().isError() && keepAlive && !isAsync() && upgradeToken == null &&
           sendfileState == SendfileState.DONE && !endpoint.isPaused()) {
		// 进入循环

        try {
            // 读取http协议的请求行[GET uri?queryString=xxx HTTP/1.1]保存到request对象中
            // 分别存放到request的method、uri、queryString、protocol属性中
            /**
             * 在读取过程中，首先是将内容从socket的读缓存区读入到SocketBufferHandler中的appReadBuffer空间（可通过配置控制是否DirectByteBuffer空间，默认不是）
             * 再从appReadBuffer空间复制到inputBuffer中的byteBuffer中；
             * （所以在分配appReadBuffer空间的时候最好是分配DirectByteBuffer空间，否则内容拷贝次数将发生3次）
             * 在读取时不会等待，有内容就读，无可读内容会返回false
             */
            if (!inputBuffer.parseRequestLine(keptAlive)) {
                // 解析http请求行失败
                if (inputBuffer.getParsingRequestLinePhase() == -1) { 
                    // 请求头是http2的请求头而失败，则需要进行协议升级操作
                    return SocketState.UPGRADING;
                } else if (handleIncompleteRequestLineRead()) {
                    // 其他情况失败，表示无法完整的读取完http协议的请求行，或者是这个连接没有发送新的http请求，
                    // 会设置readComplete=false, openSocket=true
                    // 并退出循环
                    // 退出循环之后，会判断readComplete和openSocket状态，会继续注册一个读事件到poller上
                    // 注意此时，在ConnectionHandler处理器中不会把连接与Processor的绑定关系解除，意味着下次有待读数据时依然是这个Processor实例来处理
                  	// 且读到inputBuffer中的内容也不会被清空，会跟下次读的内容整合在一起
                    break;
                }
            }

            // 会根据解析到的protocol值来确认当前的http协议版本
            prepareRequestProtocol();

            if (endpoint.isPaused()) {
                // endpoint已经暂停服务了，则设置503响应状态
                response.setStatus(503);
                // 设置错误状态
                setErrorState(ErrorState.CLOSE_CLEAN, null);
            } else {
                keptAlive = true;
                // 设置请求头的最大个数，默认值为100个
                request.getMimeHeaders().setLimit(endpoint.getMaxHeaderCount());
                // http/0.9不用解析请求头
                // 读取http协议的请求头信息保存到request对象中
                if (!http09 && !inputBuffer.parseHeaders()) {
                    // 解析请求头信息不完全
                    openSocket = true;
                    readComplete = false;
                    break;
                }
                if (!disableUploadTimeout) {
                    // 将读操作超时时间设置为连接上传数据超时时间，默认5分钟
                    socketWrapper.setReadTimeout(connectionUploadTimeout);
                }
            }
        } catch (IOException e) {
            // 读取数据过程中出现异常，则设置错误状态为立马关闭连接
            setErrorState(ErrorState.CLOSE_CONNECTION_NOW, e);
            break;
        } catch (Throwable t) {
            // 其他异常
            ExceptionUtils.handleThrowable(t);
            response.setStatus(400);
            setErrorState(ErrorState.CLOSE_CLEAN, t);
        }

        if (isConnectionToken(request.getMimeHeaders(), "upgrade")) {
            // 协议升级请求，即http请求头Connection的值中包含“upgrade”字符串就表示是一个协议升级请求
            
            // 协议升级请求，需要获取请求头“Upgrade”的值，表示需要升级到哪个协议
            String requestedProtocol = request.getHeader("Upgrade");
			
            // 获取对应升级协议实例
            UpgradeProtocol upgradeProtocol = protocol.getUpgradeProtocol(requestedProtocol);
            if (upgradeProtocol != null) {
                if (upgradeProtocol.accept(request)) {
                    // 处理协议升级的事
                    response.setStatus(HttpServletResponse.SC_SWITCHING_PROTOCOLS);
                    response.setHeader("Connection", "Upgrade");
                    response.setHeader("Upgrade", requestedProtocol);
                    action(ActionCode.CLOSE,  null);
                    getAdapter().log(request, response, 0);

                    InternalHttpUpgradeHandler upgradeHandler =
                        upgradeProtocol.getInternalUpgradeHandler(
                        getAdapter(), cloneRequest(request));
                    UpgradeToken upgradeToken = new UpgradeToken(upgradeHandler, null, null);
                    action(ActionCode.UPGRADE, upgradeToken);
                    return SocketState.UPGRADING;
                }
            }
        }

        if (getErrorState().isIoAllowed()) {
            // 允许IO操作
            rp.setStage(org.apache.coyote.Constants.STAGE_PREPARE);	// 设置当前处理阶段为准备阶段
            try {
                // 处理http请求中的各请求头的值，设置到request对象的指定属性中
                // 包括：根据Connection请求头的值是否为keep-alive来设置keepAlive变量的值
                // 处理Content-Length；Transfer-Encoding处理等
                // 根据Content-Length的情况来配置激活的InputFilter
                prepareRequest();
            } catch (Throwable t) {
                // 出现异常，设置响应状态，以及错误状态
                ExceptionUtils.handleThrowable(t);
                response.setStatus(500);
                setErrorState(ErrorState.CLOSE_CLEAN, t);
            }
        }

        // 同一个TCP连接上允许的最大的Http请求数，超过这个请求数了，keep-alive的值就会被设置为false
        // 不管Connection请求头的值是否为keep-alive
        // maxKeepAliveRequests的值在构建Http11Processor对象时指定了，默认值为100
        if (maxKeepAliveRequests == 1) {
            keepAlive = false;
        } else if (maxKeepAliveRequests > 0 &&
                   socketWrapper.decrementKeepAlive() <= 0) {
            // decrementKeepAlive()还剩余的请求数减一
            keepAlive = false;
        }

        if (getErrorState().isIoAllowed()) {
            try {
                rp.setStage(org.apache.coyote.Constants.STAGE_SERVICE); // 进入请求业务处理阶段了
                // 调用CoyoteAdapter的service方法来处理请求了
                // 到这一步就开始正式进入容器处理阶段了
                getAdapter().service(request, response);

                if(keepAlive && !getErrorState().isError() && !isAsync() &&
                   statusDropsConnection(response.getStatus())) {
                    // 本身是需要持久化的连接，但是由于响应状态是满足某些错误码的也会关闭连接
                    // 错误码为：400、408、411、413、414、500、503和501
                    setErrorState(ErrorState.CLOSE_CLEAN, null);
                }
            } catch (InterruptedIOException e) {
                // 所有异常情况下，都需要关闭连接
                setErrorState(ErrorState.CLOSE_CONNECTION_NOW, e);
            } catch (HeadersTooLargeException e) {
                if (response.isCommitted()) {
                    setErrorState(ErrorState.CLOSE_NOW, e);
                } else {
                    response.reset();
                    response.setStatus(500);
                    setErrorState(ErrorState.CLOSE_CLEAN, e);
                    response.setHeader("Connection", "close"); // TODO: Remove
                }
            } catch (Throwable t) {
                ExceptionUtils.handleThrowable(t);
                response.setStatus(500);
                setErrorState(ErrorState.CLOSE_CLEAN, t);
                getAdapter().log(request, response, 0);
            }
        }

        // 进入请求处理完成阶段
        rp.setStage(org.apache.coyote.Constants.STAGE_ENDINPUT);
        if (!isAsync()) {
            // 非异步请求，由当前线程负责结束这个http请求，包括清理在socket缓存区没有读完的请求体数据
            // 如果是异步请求，在请求处理完成的时候，AsyncContext异步请求上下文实例会负责调用endRequest()
            endRequest();
        }
        rp.setStage(org.apache.coyote.Constants.STAGE_ENDOUTPUT);	// 进入请求响应结束阶段

        if (getErrorState().isError()) {
            response.setStatus(500);	// 如果是请求出错了，设置错误状态码
        }

        if (!isAsync() || getErrorState().isError()) {
            request.updateCounters();	// 更新统计数据
            if (getErrorState().isIoAllowed()) {
                inputBuffer.nextRequest();	// 清理现场，用于处理下一个http请求时接收数据
                outputBuffer.nextRequest();
            }
        }

        if (!disableUploadTimeout) {
            // 清理现场
            int soTimeout = endpoint.getConnectionTimeout();
            if(soTimeout > 0) {
                socketWrapper.setReadTimeout(soTimeout);
            } else {
                socketWrapper.setReadTimeout(0);
            }
        }

        rp.setStage(org.apache.coyote.Constants.STAGE_KEEPALIVE); // 进入KEEPALIVE阶段

        // 方法内部设置openSocket=keepAlive的值，并判断是否需要文件发送处理
        sendfileState = processSendfile(socketWrapper);
    }	
    // 结束循环，这个循环退出的条件在于处理完第一个Http请求之后，
    // 去读取同一个连接的下一次http请求时读取不到数据或者是读取的不全就会退出循环；
    // 还有就是异常情况下会退出循环

    rp.setStage(org.apache.coyote.Constants.STAGE_ENDED);	// 设置请求处理完成

    if (getErrorState().isError() || (endpoint.isPaused() && !isAsync())) {
        // 请求处理出现错误了，或者是endpoint暂停服务了，则需要关闭当前的tcp连接
        return SocketState.CLOSED;
    } else if (isAsync()) {
        // 异步请求，返回LONG状态值
        return SocketState.LONG;
    } else if (isUpgrade()) {
        // 如果是协议升级协商，返回UPGRADING状态
        return SocketState.UPGRADING;
    } else {
        // 其他情况
        if (sendfileState == SendfileState.PENDING) {
            // 正在发送文件中的状态
            return SocketState.SENDFILE;
        } else {
            if (openSocket) {
                // 需要继续开启这个tcp连接
                if (readComplete) {
                    // 一个完整的http请求处理完毕，则继续OPEN
                    // 在连接处理器中会解除掉当前连接与Processor的绑定关系
                    return SocketState.OPEN;
                } else {
                    // 只读了一个Http请求的部分数据，则需要继续读下一个tcp包
                    // 在连接处理器中不会解除当前连接与Processor的绑定关系，
                    // 这样下一个tcp数据包来的时候还是由这个Processor处理，之前读取的数据依然
                    // 还在这个Processor的inputBuffer中
                    return SocketState.LONG;
                }
            } else {
                // 否则，关闭这个tcp连接
                return SocketState.CLOSED;
            }
        }
    }
}
```

由上述分析可知**Processor**主要负责处理的工作如下：

1. 解析Http请求协议的请求行信息保存到*coyote*包下的**Request**的相关属性中；
2. 解析Http请求协议的原始请求头信息保存到**Request**的*mimeHeaders*属性中，某些特殊请求头信息也会预处理到相关属性中；
3. 请求体的解析不在Processor中处理；
4. 负责协议升级协商的处理；
5. 调用容器层面来完成请求对应的具体业务逻辑实现，及捕捉异常处理；
6. 控制tcp连接的下一步走向：关闭、继续read等等操作；
7. dispatch的处理（在上面分析还没看出dispatch产生的源头，及作用是什么，看后续吧）。

## 三、配置参数

>maxKeepAliveRequests

控制单个TCP连接上所允许发送的最大Http请求数；默认值：100，-1表示无限制，1表示禁止http协议的keep-alive特性。

> disableUploadTimeout

用于控制在读取http协议中的请求体数据时（注意只是请求体数据），是否有超时时间限制，默认值为：false，即表示有超时时间限制，需要跟*connectionUploadTimeout*参数配合

> connectionUploadTimeout

表示读取http协议中请求体数据时的超时时间限制，及需要在connectionUploadTimeout毫秒内读取完一个Http请求的请求体中的所有数据；默认值为300000ms（5分钟）；跟*disableUploadTimeout*参数配合使用，只有在*disableUploadTimeout=false*时，此参数值才有意义。

> keepAliveTimeout

控制在上一次请求数据发送完毕之后，读取下一次请求数据之间的超时时间。意思是说，如果客户端与服务器的某个TCP连接，如果在其上发送了一次请求之后，等待了keepAliveTimeout时间之后没有发送第二次请求就会把这个TCP连接关闭。keepAliveTimeout没有设置默认值，如果为空会自动去取*connectionTimeout*这个参数的值（默认为20000ms）。

> connectionTimeout

控制的是在建立TCP连接之后，多少时间范围内没有发送数据则认为是连接超时，默认值为20000ms。与*socket.soTimeout*控制参数值一样的作用。

> restrictedUserAgents

一个java正则表达式，用于限制http请求头*user-agent*的值。默认值为空字符串。不重要的参数。

> maxSavePostSize

在用POST请求方式进行FORM和CLIENT-CERT方式的Http认证的时候，容器用于保存post数据的最大缓存空间。不重要，使用默认参数即可。

其他可控参数都不太重要，这里不再赘述了。

## 四、浏览器与Tomcat交互流程

![image-20200730155259487](\images\image-20200730224454729.png)



上图描述的是浏览器与Tomcat服务器建立了一条TCP连接之后，在这条连接之上进行Http请求的过程：

1. 通过这一条TCP连接发送的Http请求必须排队，等前一个Http请求的响应返回之后才会继续发送第二个Http请求；
2. Tomcat处理完一个Http请求，发送了这个请求的响应，不会马上再次去更新Selector选择器上的IO事件；而是先尝试以非阻塞方式继续读取这个TCP连接，看是否有数据，如果有数据则继续进行Http解析，并继续处理这个Http请求；如果没有数据，才会去Selector上继续更新一个新的读IO事件，等待下一次select操作时的读IO就绪事件处理下一次Http请求。而在有数据的情况下，又分为Http请求数据是否完整的情况，这个情况在上面的代码分析中都有，不明白的可以仔细看一看哦。不完整情况的处理其实就是针对拆包的解决方案。
3. 上图画的是浏览器与tomcat之间建立一条TCP连接的情况，多条连接时，就相当于是多个TCP连接并行了。像Chrome浏览器中针对同一个host最大允许建立六个TCP连接。

## 五、TCP连接关闭

总结一下Tomcat主动关闭一个TCP连接的情况：

1. 超时
   - 连接超时：连接建立成功之后，在connectionTimeout（默认20s）时间内没有发送数据。
   - 读超时：在发送完一次请求之后，在keepAliveTimeout（默认取connectionTimeout值）时间内没有再次发送数据。
   - 数据上传超时：在一次http请求中，读取http请求体数据没有在connectionUploadTimeout（默认5分钟）时间内读完或者是发送完。
2. 业务层面的未捕捉异常：会造成500响应码，同时关闭这个TCP连接；
3. 同一个TCP连接上发送的Http请求数超过了maxKeepAliveRequests数（默认100）；
4. 其他IO异常和违反了Http协议约束的异常。