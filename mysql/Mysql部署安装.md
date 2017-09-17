## Mysql部署安装

安装环境：
​	操作系统：Centos7
​        MySQL：5.7

安装方式：yum安装，[参考官网](https://dev.mysql.com/doc/refman/5.7/en/linux-installation-yum-repo.html)

步骤：

1. 第一步：添加MySQL5.7的yum仓库
   从mysql官网下载mysql7的仓库包，并上传到Centos，[点击下载](https://dev.mysql.com/downloads/repo/yum/)
   执行命令：`yum localinstall mysql-repository.rpm`
2. 第二步：选择mysql的发布版本，我下载的是mysql5.7的仓库包，所以默认5.7版本是放开的，如果你想安装其他版本的，你必须通过执行命令或者修改 /etc/yum.repos.d/mysql-community.repo这个配置文件来激活你想安装的对应版本
3. 第三步：通过yum安装mysql，执行如下命令等待下载文件安装
   `yum install mysql-community-server`
4. 第四步：启动MySQL服务`service mysqld start`，查看mysql服务状态：`service mysqld status`
5. 第五步：连接mysql并重设密码
   安装好mysql服务后，会有一个默认密码，默认密码获取方式通过查看日志文件内容可得，可执行如下命令
   `grep 'temporary password' /var/log/mysqld.log`
   根据密码登入mysql，执行如下命令重设密码：
   `ALTER USER 'root'@'localhost' IDENTIFIED BY 'MyNewPass4!';`
6. 第六步：配置远程访问：
   `GRANT ALL PRIVILEGES ON *.* TO 'root'@'%' IDENTIFIED BY 'password' WITH GRANT OPTION;`
   `flush privileges;`

