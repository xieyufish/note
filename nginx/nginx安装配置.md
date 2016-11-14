## Nginx基本安装和配置

​	Nginx是一个很强大的高性能Web和反向代理服务器，在项目中常被用作负载均衡服务器，也可以作为邮件代理服务器。本文主要介绍Nginx在linux(**CentOS7**)中的基本安装和配置。

### 1. 安装

​	本来想直接通过yum方式来安装，但是可能yum源没有配置正确没有再我配置的yum源上找到ngnix相关的rpm包，所以觉得采取源码的安装方式。

**依赖包安装**

在正式安装nginx之前，我们要先安装它所依赖的包，我们可以通过以下命令来完成nginx依赖包的安装：
**yum install -y gcc gcc-c++ pcre pcre-devel zlib zlib-devel openssl openssl-devel**

**创建nginx相关目录**

执行命令**mkdir -p /usr/local/nginx /var/log/nginx /var/tmp/nginx /var/lock/nginx**，其中/usr/local/nginx用来保存nginx的安装配置、启动等文件的目录，/var/log/nginx用来存放nginx运行产生的日志文件，/var/tmp/nginx用来存放临时文件，/var/lock/nginx用来存放进程相关的文件。

**下载源码包**

我们有多种方式来获得nginx的源码包。可以直接通过官网下载，或者通过wget命令直接在命令行下载，我选择的是通过命令行方式下载源码包，写这篇文章时的最新稳定版源码包版本为1.10.2，可通过如下命令下载：
**wget http://nginx.org/download/nginx-1.10.2.tar.gz**

**编译配置**

我们要将nginx安装在我们事先规划好的目录结构中，那么我们必须要先执行配置编译来指定我们创建好的目录，定位工作目录到解压后的nginx源码目录下，执行如下命令：

```
./configure \
--prefix=/usr/local/nginx \
--pid-path=/var/local/nginx/nginx.pid \
--lock-path=/var/lock/nginx/nginx.lock \
--error-log-path=/var/log/nginx/error.log \
--http-log-path=/var/log/nginx/access.log \
--with-http_gzip_static_module \
--http-client-body-temp-path=/var/tmp/nginx/client \
--http-proxy-temp-path=/var/tmp/nginx/proxy \
--http-fastcgi-temp-path=/var/tmp/nginx/fastcgi \
--http-uwsgi-temp-path=/var/tmp/nginx/uwsgi \
--http-scgi-temp-path=/var/tmp/nginx/scgi
```

然后执行编译：**make**
安装：**make install**

**启动Nginx**

- 先检查在目录/usr/loca/nginx中是否生成如下目录文件：

  ```
  drwxr-xr-x. 2 root root 4096 11月  8 18:48 conf
  drwxr-xr-x. 2 root root   38 11月  8 18:48 html
  drwxr-xr-x. 2 root root   18 11月  8 18:48 sbin
  ```

- nginx的默认监听端口为80，在启动nginx之前我们需要关闭防火墙或者将80端口开放：
  关闭防火墙可以执行这个命令：**systemctl stop firewalld**
  开放端口可以执行这个命令：**firewall-cmd --zone=public --add-port=80/tcp --permanent**

- 启动nginx：
  执行 /usr/local/nginx/nginx 启动nginx

- 检查是否有Nginx进程：ps -aux | grep nginx，正常时会有如下三个进程：

  ```
  root      16475  0.0  0.1  24468   768 ?        Ss   18:59   0:00 nginx: master process sbin/nginx
  nobody    16476  0.0  0.3  24888  1488 ?        S    18:59   0:00 nginx: worker process
  root      16478  0.0  0.2 112664   980 pts/0    S+   18:59   0:00 grep --color=auto nginx
  ```

- 检查nginx是否启动并监听了80端口：netstat -ntlup | grep 80

  ```
  tcp        0      0 0.0.0.0:80              0.0.0.0:*               LISTEN      16475/nginx: master 
  ```

- 浏览器访问：

   ![6](images\6.png)

### 2. 将Nginx配置为系统服务

- 新建文件：touch /etc/init.d/nginx

- 编辑文件内容：

  ```shell
  #!/bin/bash


  #nginx执行程序路径需要修改
  nginxd=/usr/local/nginx/sbin/nginx

  # nginx配置文件路径需要修改
  nginx_config=/usr/local/nginx/conf/nginx.conf

  # pid 地址需要修改
  nginx_pid=/var/local/nginx/nginx.pid


  RETVAL=0
  prog="nginx"

  # Source function library.
  . /etc/rc.d/init.d/functions
  # Source networking configuration.
  . /etc/sysconfig/network
  # Check that networking is up.
  [ ${NETWORKING} = "no" ] && exit 0
  [ -x $nginxd ] || exit 0

  # Start nginx daemons functions.
  start() {
  if [ -e $nginx_pid ];then
     echo "nginx already running...."
     exit 1
  fi

  echo -n $"Starting $prog: "
  daemon $nginxd -c ${nginx_config}
  RETVAL=$?
  echo
  [ $RETVAL = 0 ] && touch /var/lock/subsys/nginx
  return $RETVAL
  }

  # Stop nginx daemons functions.
  # pid 地址需要修改
  stop() {
      echo -n $"Stopping $prog: "
      killproc $nginxd
      RETVAL=$?
      echo
      [ $RETVAL = 0 ] && rm -f /var/lock/subsys/nginx /var/local/nginx/nginx.pid
  }

  # reload nginx service functions.
  reload() {
      echo -n $"Reloading $prog: "
      #kill -HUP `cat ${nginx_pid}`
      killproc $nginxd -HUP
      RETVAL=$?
      echo
  }

  # See how we were called.
  case "$1" in
      start)
          start
          ;;
      stop)
          stop
          ;;
      reload)
          reload
          ;;
      restart)
          stop
          start
          ;;
      status)
          status $prog
          RETVAL=$?
          ;;
      *)

      echo $"Usage: $prog {start|stop|restart|reload|status|help}"
      exit 1

  esac
  exit $RETVAL
  ```

- 修改权限：chmod 755 /var/init.d/nginx

- 启动服务：service nginx start

- 停止和重启：service nginx stop|restart

### 3. 反向代理简单配置

​	修改nginx配置文件：

```properties
worker_processes  1;

events {
    worker_connections  1024;
}

http {
    include       mime.types;
    default_type  application/octet-stream;

    sendfile        on;
    keepalive_timeout  65;

    # 自己定义的两个 tomcat 请求地址和端口
    # 也就是当浏览器请求：tomcat.youmeek.com 的时候从下面这两个 tomcat 中去找一个进行转发
    upstream tomcatCluster {
        server 192.168.146.175:8080;
        server 192.168.146.176:8080;

        # 添加 weight 字段可以表示权重，值越高权重越大，默认值是 1，最大值官网没说，一般如果设置也就设置 3,5,7 这样的数
        # 官网：https://www.nginx.com/resources/admin-guide/load-balancer/#weight
        # server 192.168.1.114:8080 weight=2;
        # server 192.168.1.114:8081 weight=1;
    }

    server {
        listen       80;
        server_name  tomcat.youmeek.com;

        location / {
            proxy_pass   http://tomcatCluster;
            index  index.html index.htm;
        }
    }
}
```

浏览器访问测试：
![7](images\7.png)

![8](images\8.png)

![9](images\9.png)

![10](images\10.png)

从浏览器访问结果可知，当我们访问nginx服务器时，会在175和176两个主机的资源上进行切换。