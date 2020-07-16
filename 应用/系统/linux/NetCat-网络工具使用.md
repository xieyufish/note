## NetCat-网络工具使用

​	NetCat是linux中一个常用的网络工具，通过这个工具我们可以测试网络，勘探网络等操作。

### NetCat的安装

​	我的环境：CentOS7

**安装步骤**

- 通过wget http://sourceforge.net/projects/netcat/files/netcat/0.7.1/netcat-0.7.1-1.i386.rpm获取netcat的rpm包
- 执行命令：rpm -ivh netcat-0.7.1-1.i386.rpm安装时，会报确实依赖

​       ![netcat_1](images\netcat_1.png)

- 这是因为确实glibc.i686依赖的原因，执行yum list glibc* ![netcat_2](images\netcat_2.png)
- 接着执行yum install glibc.i686命令，安装依赖包glibc.i686 ![netcat_3](images\netcat_3.png)
- 重新执行命令：rpm -ivh netcat-0.7.1-1.i386.rpm，安装成功

​       ![netcat_4](images\netcat_4.png)

- 执行nc -help查看是否安装成功 ![netcat_5](images\netcat_5.png)

- 运行nc -l -p 9999命令，在本机的9999端口监听tcp请求

- 远程机器可以通过nc hostip port连接到nc命令开放的端口

  **注意：当我们使用nc -l -p 9999命令在9999端口监听，并在远程通过nc hostip port连接到此监听端口之后，再在其他机器上通过nc hostip port想连接到此侦听端口时，发现输入命令之后没有任何反应，那我们可以通过添加 -v 选项参数  nc -v hostip port来查询连接过程的详细信息，我们会发现是被连接是被拒绝的，开始我以为是哪里配置出了问题，其实不是，在所有机器上测试之后发现，只有第一次发起连接的机器能够连接上，应该是nc服务监听端口只能够处理一次连接请求。**

