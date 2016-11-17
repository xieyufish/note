## Redis源码安装(单节点)

**IP ：** ==192.168.146.171==
**环 境：** ==CentOS 7==
**Redis  版 本 ：** redis-3.2.5 
**安装目录 ：** /usr/local/redis
**用 户 ：**root



**编译和安装所需的包:**
yum install gcc tcl

**下载 3.2.5 版 Redis（当前最新版 redis-3.2.5.tar.gz，请学员们在安装时自行选用最新版）**
cd /usr/local/src
wget http://download.redis.io/releases/redis-3.2.5.tar.gz

**解压:**
tar -zxvf redis-3.2.5.tar.gz
cd redis-3.2.5

**创建安装目录：**
mkdir /usr/local/redis

**安装(使用 PREFIX 指定安装目录)：**
make PREFIX=/usr/local/redis install
安装完成后，可以看到/usr/local/redis 目录下有一个 bin 目录，bin 目录里就是 redis 的命令脚本：
redis-benchmark redis-check-aof redis-check-dump redis-cli redis-server

**将 Redis 配置成服务：**
按上面的操作步骤，Redis 的启动脚本为：/usr/local/src/redis3.2.5/utils/redis_init_script
将启动脚本复制到/etc/rc.d/init.d/目录下，并命名为 redis:
cp /usr/local/src/redis3.2.5/utils/redis_init_script /etc/rc.d/init.d/redis
编辑/etc/rc.d/init.d/redis，修改相应配置，使之能注册成为服务：
```shell
#!/bin/sh
#chkconfig: 2345 80 90
#
# Simple Redis init.d script conceived to work on Linux systems
# as it does use of the /proc filesystem.

REDISPORT=6379
EXEC=/usr/local/redis/bin/redis-server
CLIEXEC=/usr/local/redis/bin/redis-cli

PIDFILE=/var/run/redis_${REDISPORT}.pid
CONF="/usr/local/redis/conf/${REDISPORT}.conf"

case "$1" in
    start)
        if [ -f $PIDFILE ]
        then
                echo "$PIDFILE exists, process is already running or crashed"
        else
                echo "Starting Redis server..."
                $EXEC $CONF &
        fi
        ;;
    stop)
        if [ ! -f $PIDFILE ]
        then
                echo "$PIDFILE does not exist, process is not running"
        else
                PID=$(cat $PIDFILE)
                echo "Stopping ..."
                $CLIEXEC -p $REDISPORT shutdown
                while [ -x /proc/${PID} ]
                do
                    echo "Waiting for Redis to shutdown ..."
                    sleep 1
                done
                echo "Redis stopped"
        fi
        ;;
    *)
        echo "Please use start or stop as first argument"
        ;;
esac
```
查看以上 redis 服务脚本，做如下几个修改的准备：
(1) 在脚本的第一行后面添加一行内容如下：
​	chkconfig: 2345 80 90
​	（如果不添加上面的内容，在注册服务时会提示：service redis does not support chkconfig）
(2) REDISPORT 端口保持 6379 不变；(注意，端口名将与下面的配置文件名有关)
(3) EXEC=/usr/local/bin/redis-server 改为 EXEC=/usr/local/redis/bin/redis-server
(4) CLIEXEC=/usr/local/bin/redis-cli 改为 CLIEXEC=/usr/local/redis/bin/redis-cli
(5) 配置文件设置：
​	创建 redis 配置文件目录
​	mkdir /usr/local/redis/conf
​	复制 redis 配置文件/usr/local/src/redis3.2.5/redis.conf 到/usr/local/redis/conf 目录并按端口号重命名为 		6379.conf
​	cp /usr/local/src/redis3.2.5/redis.conf /usr/local/redis/conf/6379.conf
​	做了以上准备后，再对 CONF 属性作如下调整：
​	CONF="/etc/redis/\${REDISPORT}.conf" 改为 CONF="/usr/local/redis/conf/${REDISPORT}.conf"
(6) 更改 redis 开启的命令，以后台运行的方式执行:
​	\$EXEC $CONF & #“&”作用是将服务转到后面运行

以上配置操作完成后，便可将 Redis 注册成为服务：
​    chkconfig --add redis

修改 redis 配置文件设置：
​    vim /usr/local/redis/conf/6379.conf
修改如下配置
​    daemonize no 改为> daemonize yes

启动 Redis 服务
​    service redis start
将 Redis 添加到环境变量中：
​    vim /etc/profile
在最后添加以下内容：
​    export PATH=$PATH:/usr/local/redis/bin
使配置生效：
​    source /etc/profile
现在就可以直接使用 redis-cli 等 redis 命令了

关闭 Redis 服务
​    service redis stop