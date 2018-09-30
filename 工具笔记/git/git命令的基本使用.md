##Git命令

### 一、Git的基本常用命令

#### 仓库的创建和获取

git提供了两种创建仓库的方式，分别如下。

> init命令

创建一个新的项目或在已有的项目路径下，打开git bash命令行窗口，输入如下命令：

``````shell
git init
``````

即表示在当前目录中创建了仓库，会在当前文件夹下新建一个`.git`目录，用于存放所有版本管理相关的文件和数据等；再通过`git add <file>`和`git commit -m 'message'`接口添加和提交文件到创建的仓库中。



> clone命令

`git init`命令是直接在本地新建仓库，那么如果我们要将远程或者公司内部git仓库拷贝一个仓库到本地，又应该怎么办？如下命令：

``````shell
git clone <url>
``````

就是做这件事情的，它以指定url的方式将指定的远程仓库完全拷贝到本地。例如：在命令行中执行如下命令：

``````shell
git clone https://github.com/alibaba/easyexcel.git
``````

将从github上将easyexcel这个仓库下载拷贝到当前目录的easyexcel目录中，如果你想将其放在当前目录的其他目录中，则可以执行：

``````shell
git clone https://github.com/alibaba/easyexcel.git myeasyexcel
``````

这个命令，将会把远程的easyexcel目录中的文件拷贝到当前目录的myeasyexcel文件夹中。

#### 将修改记录到仓库中

首先，git针对工作的目录中的文件，有几种状态区分，如下图：

![](images/1.jpg)

工作目录中的所有文件被分为两大类：*tracked*和*untracked*：

- tracked：表示文件已经被git管理跟踪起来的文件，他们可以是unmodified、modified和staged，简而言之就是git知道这些文件的存在；
- untracked：表示git不知道的，简单理解就是刚刚新创建的文件且还没有被add或commit的文件；



> status命令

作用：跟踪当前工作目录下，各个文件当前的状态；

通过执行：

``````shell
git status
``````

我们可以知道哪些文件是untracked，哪些tracked的文件modified过，哪些被add了，当前可以commit的是哪些文件等。

``````shell
$ git status
On branch master
Changes to be committed:
  (use "git reset HEAD <file>..." to unstage)

        modified:   README.md

Changes not staged for commit:
  (use "git add <file>..." to update what will be committed)
  (use "git checkout -- <file>..." to discard changes in working directory)

        modified:   LISENCE

Untracked files:
  (use "git add <file>..." to include in what will be committed)

        config
``````

上面是在某些操作之后执行status命令之后的结果，其中Changes to be committed：列表展示的是文件有修改了的，且通过执行add命令将文件提交到staged区的文件；而Changes not staged for commit：列表展示的是文件有修改的，但是还没有执行add命令将文件提交到staged区的文件；Untracked files：列表则展示的是新创建的文件，且还没有执行add命令将文件交给git管理的，如果执行git add config命令，那么config将会加到Changes to be commited：列表下去，如下：

``````shell
On branch master
Changes to be committed:
  (use "git reset HEAD <file>..." to unstage)

        modified:   README.md
        new file:   config

Changes not staged for commit:
  (use "git add <file>..." to update what will be committed)
  (use "git checkout -- <file>..." to discard changes in working directory)

        modified:   LISENCE
``````

status命令默认输出的信息是完整的，如果我们只想看简单的状态信息可以在status命令后增加-s或--short参数，如下：

``````shell
$ git status -s
 M LISENCE
M  README.md
A  config
``````



> add命令

作用：将新建文件或者是修改过的文件加入到staged区

``````shell
git add file
``````

在我们修改一个tracked文件或者是新建一个文件后，我们可以通过上述命令，将新增的或者是修改后的文件提交到staged区；注意此时文件还只是在staged区，并没有被提交到仓库中。执行了add命令的文件，在status命令的结果中，将被列在Changes to be committed的列表中。

如果我们一次性有很多文件要add，很显然一个一个的add是非常麻烦且耗时的，add提供了命令参数-A可以让我们一次性将所有修改过和新建的文件提交到staged区，如下：

``````shell
git add -A
``````



> .gitignore文件

作用：指定哪些文件是需要git忽略的

我们在使用某些构建工具时，会自动在我们的工作目录下创建一些文件或者文件夹，比如maven会自动创建target目录；那么我们在使用git时，如何自动忽略掉这些文件呢？在工作目录下创建一个.gitignore文件，在文件中指定要忽略的文件列表即可；比如：

``````
$ cat .gitignore
*.[oa]
*~
``````

会忽略以.o或者.a结尾的文件，以及忽略以～结尾的文件，.gitignore文件中支持正则表达式。



> diff命令

作用：比较版本之间的变化。

当我们修改了某些文件或者add了一些文件之后，我们想查看这一次做了哪些改变，status命令只能告诉我们哪些文件有变化，但是具体改变的内容并不知道，所以git提供diff命令供我们查看具体的改变内容。

``````shell
git diff
``````

比较工作目录中的文件与staged区的文件，并列出改变的内容；

``````shell
git diff --staged
``````

比较staged区的文件与仓库中的文件，并列出改变内容。



> commit命令

作用：将staged区的文件提交到仓库。

在我们将文件add到staged区之后，文件并没有提交到仓库，我们还需要执行commit命令将staged区的文件提交到仓库中；注意：commit提交的只是staged区的内容，也就是说如果你在最后一次add命令之后，还修改了文件且没有add到staged区，那么commit的时候并不会把你最后的修改提交。

``````shell
git commit -m '提交注释'
``````

有时我们不想add之后在commit，因为感觉这样麻烦，那么我们可以执行：

``````shell
git commit -a -m '提交注释'
``````

上面这个命令会将所有的修改直接提交到仓库，而不需要执行add命令；但是不建议直接这样做。



> rm命令

作用：移除文件，将文件从工作目录和staged区删除

有时，我们可能需要删除某些文件，那么我们可以执行：

``````shell
git rm file
``````

执行这个命令之后，会将file从工作目录和staged区同时删除，然后我们再执行commit命令即可从仓库中将指定文件删除；有时，我们想继续将文件保留在工作目录，只想将文件从staged区删除，那么我们可以执行如下命令

``````shell
git rm --cached file
``````



> mv命令

作用：移动或者重命名文件

``````shell
git mv file_from file_to
``````

这等价于下面三条命令：

``````shell
mv file_from file_to
git rm file_from
git add file_to
``````

