##Git命令

### 一、Git的基本常用命令

#### 仓库的创建和获取

git提供了两种创建仓库的方式，分别如下。

> git init命令

创建一个新的项目或在已有的项目路径下，打开git bash命令行窗口，输入`git init`命令，即表示在当前目录中创建了仓库，会在当前文件夹下新建一个`.git`目录，用于存放所有版本管理相关的文件和数据等；再通过`git add <file>`和`git commit -m 'message'`接口添加和提交文件到创建的仓库中。

> git clone命令

`git init`命令是直接在本地新建仓库，那么如果我们要将远程或者公司内部git仓库拷贝一个仓库到本地，又应该怎么办？`git clone <url>`命令就是做这件事情的，它以指定url的方式将指定的远程仓库完全拷贝到本地。例如：在命令行中运行`git clone https://github.com/alibaba/easyexcel.git`命令，将从github上将easyexcel这个参考下载拷贝到当前目录的easyexcel目录中，如果你想将其放在当前目录的其他目录中，可以执行`git clone https://github.com/alibaba/easyexcel.git myeasyexcel`这个命令，将会把远程的easyexcel目录中的文件拷贝到当前目录的myeasyexcel文件夹中。

#### 将修改记录到仓库中



