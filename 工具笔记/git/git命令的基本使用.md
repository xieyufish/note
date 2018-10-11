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



####查看提交历史命令

> log命令

作用：查看所有的提交历史记录信息

我们将文件提交到git仓库之后，随着时间的过去和提交次数变多，我们想查看之前每次提交时所做的改变，那么log命令就很有用了，它可以让你查看你的所有提交记录，执行：

``````shell
git log
``````

执行这个命令之后，会以分页的方式将所有的提交历史信息显示出来；log命令提供了很多的参数让我们可以过滤出我们想关注和查看的指定提交，比如：

``````shell
git log -p
``````

列出每次提交具体修改的内容信息；

``````shell
git log -2
``````

列出最近的2次提交信息，2可以换成任意正整数；

``````shell
git log -stat
``````

列出每次提交的简要信息，比如修改了哪个文件，哪个文件增加了多少，删除了多少行等信息；

``````shell
git log --pretty=oneline
``````

将每次的提交信息美化之后打印出来，提供了不同的值：short、full、fuller，提供了一个强大的format值，可以按照指定的格式输出信息：

``````shell
git log --pretty=format:"%h - %an, %ar : %s"
``````

format选项值：

| Option | Description of Output                           |
| ------ | ----------------------------------------------- |
| %H     | Commit hash                                     |
| %h     | Abbreviated commit hash                         |
| %T     | Tree hash                                       |
| %t     | Abbreviated tree hash                           |
| %P     | Parent hashes                                   |
| %p     | Abbreviated parent hashes                       |
| %an    | Author name                                     |
| %ae    | Author email                                    |
| %ad    | Author date (format respects the --date=option) |
| %ar    | Author date, relative                           |
| %cn    | Committer name                                  |
| %ce    | Committer email                                 |
| %cd    | Committer date                                  |
| %cr    | Committer date, relative                        |
| %s     | Subject                                         |

log的常用参数选项：

| Option          | Description                                                  |
| --------------- | ------------------------------------------------------------ |
| -p              | Show the patch introduced with each commit.                  |
| --stat          | Show statistics for files modified in each commit.           |
| --shortstat     | Display only the changed/insertions/deletions line from the --stat command. |
| --name-only     | Show the list of files modified after the commit information. |
| --name-status   | Show the list of files affected with added/modified/deleted information as well. |
| --abbrev-commit | Show only the first few characters of the SHA-1 checksum instead of all 40. |
| --relative-date | Display the date in a relative format (for example, “2 weeks ago”) instead of using the full date format. |
| --graph         | Display an ASCII graph of the branch and merge history beside the log output. |
| --pretty        | Show commits in an alternate format. Options include oneline, short, full, fuller, and format (where you specify your own format). |
| --oneline       | Shorthand for --pretty=oneline --abbrev-commit used together. |

**限制log命令的输出**

| Option            | Description                                                  |
| ----------------- | ------------------------------------------------------------ |
| -\<n>             | Show only the last n commits                                 |
| --since, --after  | Limit the commits to those made after the specified date.    |
| --until, --before | Limit the commits to those made before the specified date.   |
| --author          | Only show commits in which the author entry matches the specified string. |
| --committer       | Only show commits in which the committer entry matches the specified string. |
| --grep            | Only show commits with a commit message containing the string |
| -S                | Only show commits adding or removing code matching the string |

``````shell
git log -3 # 显示最近3此的提交信息
git log --since=2.weeks # 显示最近两个星期的提交记录
git log --since="2018-01-01" #显示从2018-01-01日以来的提交记录
git log --since="2 years 1 day 3 minutes ago" #显示从2年一天3分钟之前以来的提交记录
git log -S "sayHello" #显示修改内容包含sayHello字符的提交记录
``````

所有的这些命令参数可以联合一起使用

``````shell
git log --pretty="%h - %s" --author='Junio C Hamano' --since="2008-10-01" \
 --before="2008-11-01" --no-merges -- t/
``````

两个中划线(--)指定文件夹或者文件名字，这个命令参数必须放在最后面。



#### 撤销修改命令

> reset命令

作用：重置某个文件的状态，或者说将某个文件从stage区重置为unstaged状态

某些情况下，我们将一个修改了的，但是不还不想提交到stage区域的文件add到了stage区域，我们可以通过reset命令来讲这个文件从stage区域清楚，让其返回到unstaged的状态，执行：

``````shell
git reset HEAD file
``````



> checkout命令

作用：将修改的文件恢复到上一次的状态

我们修改了一个文件之后，没有提交到stage区域，如果在这个时候要将这些修改清除，那么可以执行：

``````shell
git checkout -- file
``````



#### 远程仓库命令

> remote命令

作用：查看你配置的远程仓库服务器地址

``````shell
git remote
``````

显示你配置的远程仓库服务器在你本地的简称

``````shell
git remote -v
``````

显示远程仓库的简称和对应的服务器仓库地址路径

``````shell
git remote add <shortname> <url>
``````

添加一个远程仓库地址路径url，并制定一个简称名字shortname



> fetch|pull命令

作用：将远程仓库更新下载到本地

区别：

fetch：只下载到本地，不会自动合并到你的工作目录

pull：下载到本地之后，会自动合并到你的工作目录

``````shell
git fetch <remote-shortname>
``````



> push命令

作用：将你本地仓库或者修改推送到远程仓库

``````shell
git push <remote> <branch>
``````



**查看远程仓库的详细信息**

``````shell
git remote show <remote-shortname>
``````

此命令会列出远程仓库的路径地址，以及远程仓库上的分支信息

 **重命名和删除远程仓库**

``````shell
git remote rename <from-remote-name> <to-remote-name>
``````

``````shell
git remote remove <remote-name>
``````



#### 标签命令

标签命令用于在发布版本时用。

git中有两种标签：

- *lightweight*: 跟分支很像，它只是指向一个特定提交点的指针
- *annotated*: 会包含标签名、邮件地址和日期等信息存在仓库数据中，常用的是这种标签。

> tag命令

``````shell
git tag
``````

列出打过的所有标签名

``````shell
git tag -l "v1.8.5*"
``````

列出匹配v1.8.5\*的所有标签



``````shell
git tag -a v1.4 -m "my version 1.4"
``````

添加一个annotated类型的标签，-m指定标签的信息

``````shell
git show v1.4
``````

显示标签v1.4的信息



``````shell
git tag v1.4-aa
``````

添加一个lightweight类型的标签

``````shell
git show v1.4-aa
``````



``````shell
git tag -a v1.2 9fceb02
``````

有时我们在某个提交完成之后会忘了打标签，但是接着又提交了多次，那么如果我们还想在之前某个提交处打标签怎么办呢？上面命令就是在指定的提交处(9fceb02：某次提交的hash码缩写值)打标签的命令

``````shell
git push <remote-name> <tagname>
``````

将我们打的某个标签信息数据提交到指定的远程仓库地址上，如果我们有很多标签信息需要一次提交到远程仓库上怎么办呢？可以执行如下命令：

``````shell
git push <remote-name> --tags
``````

上述命令会将本地仓库还没有提交的标签信息提交到远程仓库上



``````shell
git tag -d v1.4
``````

在本地仓库中删除名称为v1.4的标签

``````shell
git push <remote-name> :refs/tags/v1.4
``````

在远程仓库名字为\<remote-name>的仓库中删除名称为v1.4的标签



#### 取别名命令

有时候有些命令很长，我们输入太多字符嫌麻烦，那么我们可以按照自己的习惯给这些命令取一个简单的别名。

``````shell
git config --global alias.co checkout	# chekout命令别名为co
git config --global alias.br branch	    # brach命令别名为br
git config --global alias.ci commit     # commit命令别名为ci
git config --global alias.st status     # status命令别名为st
``````

执行完上面命令之后，如果我们要提交文件到仓库就只需执行：`git ci`即可





