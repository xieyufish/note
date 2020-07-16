## Hadoop集群管理工具-Ambari使用

​	环境：CentOS7，Jdk1.7.0_79

### 1. Ambari的安装

​	官网提供的首要安装方式是源码安装，但是那种安装方式太麻烦。我这里采取的是官网有介绍的yum安装方式。

​	获取Ambari的公共库文件，这里选择的ambari版本为**2.4.0**，在CentOS上执行如下命令：**wget http://public-repo-1.hortonworks.com/ambari/centos7/2.x/updates/2.4.0.1/ambari.repo**；执行安装命令：**yum install ambari-server**；安装完成之后，可以直接执行：**ambari-server setup**命令来完成对ambari的配置，在这些配置信息中，ambari默认会使用Postgres数据库，Oracle的JDK，我们要修改JDK为我们自己安装的jdk文件位置。

![14](images\14.png)

启动Ambari：**ambari-server start**

![15](images\15.png)

浏览器访问：用户名密码都为admin

### 2. 在ambari上安装hadoop集群环境

