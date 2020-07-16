# reset和revert命令

在被git管理的文件夹下，有时我们会做一些误操作或者说是一些后悔的操作。那么在文件已经同步到本地仓库中的情况下，有什么方式让我撤销这些操作呢？

针对reset和revert命令，大家可能很面熟，可能也都用过，知道这两个命令都可以做一些撤销的操作。我也曾用过这两个命令，但是用着两个命令的时候也是带着一脸懵逼的状态去用的，这两个命令到底有什么区别一直没有深究。今天，我将研究这两个命令之间的异同。

##reset命令

重置工作目录，会把当前的工作目录恢复到历史的某个状态。它的常见使用方式有：

``````shell
git reset HEAD <file>
git reset --hard <commitid>
git reset HEAD^  # 恢复到当前提交的前一个提交，不同的表示方式而已
git reset HEAD^n
``````

> git reset HEAD <file>

此命令的使用场景是：当我们修改了工作空间的某个文件，并通过add命令将文件提交到了stage区；如果此时我们后悔了，想将文件从stage区移除，则可以通过使用此命令进行从stage区移除，执行此命令之后你此次做的修改并不会丢失，只不过是不再stage区而已，你依然可以通过add命令加入到stage区再commit。

> git reset --hard <commitid>

将工作空间目录恢复到指定提交(commitid)时的状态。此命令会将发生在commitid之后的提交给删除(在提交历史中不再出现，但是依然存在)，将工作目录恢复到commitid时的状态。用图表示如下：

![](https://imgconvert.csdnimg.cn/aHR0cDovL2ltZy5ibG9nLmNzZG4ubmV0LzIwMTgwNDE0MjEyMjIxMDMz)

如果你在执行reset命令之后后悔了，那么你只要记得你之前提交的commitid(如版本二的commitid)，那么你依然可以通过git reset commitid恢复到你想要的版本历史处。如果你不知道对应的commitid，一般情况下可以通过git   reflog命令找到。

## revert命令

撤销某一个commitid执行的操作，注意这里只是某一个commitid，并不会把此commitid之后的commitid所做的操作也给撤销；这也是我以前一直误解的地方，我一直以为会把之后的所有提交都撤销。

此命令的常见使用方式：

``````shell
git revert -n <commitid> # 先撤销
git commit -m # 在提交
``````

此命令一般会伴随着冲突，在手动解决冲突之后，可以通过git revert --continue命令继续revert，或者通过git revert --abort来中止此次revert操作。用图表示revert命令，如下：

![](https://imgconvert.csdnimg.cn/aHR0cDovL2ltZy5ibG9nLmNzZG4ubmV0LzIwMTgwNDE0MjA1ODE2MTg4)

我们可以看到revert命令并不会将提交历史给去掉，而是会形成一个新的提交历史，这个提交历史就是撤销了你指定提交的指定操作。其实有点类似于：我们通过手动将我们想撤销的动作做完之后再提交的意思，只不过git通过revert命令来让这个过程自动化进行。

好了，此两个命令的常用情况讲解就到此为止，欢迎大家一起讨论。