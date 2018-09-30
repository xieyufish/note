## Mysql之InnoDB存储引擎中的锁和事物模型

### 一、锁

Mysql中InnoDB存储引擎中锁类型有：

- 共享锁和独占锁（Shared and Exclusive Locks）；
- 意向锁（Intention Locks）；
- 记录锁（Record Locks）；
- 区间锁（Gap Locks）；
- Next-Key Locks；
- 插入意向锁（Insert Intention Locks）；
- AUTO-INC Locks；
- Predicate Locks for Spatial Indexes。

#### 共享锁和独占锁

InnoDB实现了两种行级别的锁，共享锁（S）和独占锁（X）。

- 共享锁：允许持有共享锁的事物读取一行；
- 独占锁：允许持有独占锁的事物更新或者删除一行；

如果事物T1持有数据行r的一个共享锁（S锁），那么其他不同的事物T2向数据行r请求锁的处理如下：

- 如果T2请求一个S锁会被快速获得，这样就是T1和T2都拥有r的一个S锁；
- 如果T2请求一个X锁，那么T2不会马上获得；

如果T1持有数据行r的是一个X锁，那么不管T2是获取什么类型的锁都必须等到T1释放行r的锁。

#### 意向锁

InnoDB支持多粒度锁同时存在，也就是说允许行级别的锁和整个表级别的锁同时存在。InnoDB就是通过另外的一个锁类型—意向锁，来实现锁同时存在功能的。意向锁是表级别的锁，它表示一个事物想要获取的是表中行什么类型的锁（S或者X锁），这就表示也存在两种类型的意向锁：

- 共享锁意向锁（IS锁）：表示事物想获取表格t中某些行的S锁；
- 独占锁意向锁（IX锁）：表示事物想获取表格中某些行的X锁；

意向锁规则：

- 在一个事物能获取表格t中行r的S锁之前，它必须先获取到表格t的IS锁或者比IS锁更强的锁；
- 在一个事物能获取表格t中行r的X锁之前，它必须先获取到表格t的IX锁。

锁类型兼容性如下表：

|      | X    | IX   | S    | IS   |
| ---- | ---- | ---- | ---- | ---- |
| X    | 冲突 | 冲突 | 冲突 | 冲突 |
| IX   | 冲突 | 兼容 | 冲突 | 兼容 |
| S    | 冲突 | 冲突 | 兼容 | 兼容 |
| IS   | 冲突 | 兼容 | 兼容 | 兼容 |

一个事物相获取数据的锁时，如果要获取的锁与已经存在这些数据上的锁是兼容的，那么这个事物能立刻获取到锁；如果是冲突的，那么这个事物必须的带已经存在的锁被释放。

意向锁的作用是表示有事物正在锁住某行，或者打算锁住某行。

#### 记录锁

记录锁是锁在索引记录上的锁。举个例子，比如语句：`select c1 from t where c1 = 10 for update;`会阻止其他事物插入、更新或者删除t.c1=10的记录。

记录锁总是锁在索引记录上，即使表格不存在索引，也会锁在表格自己创建的隐藏聚簇索引上。

#### 区间锁

一个区间锁是锁在一个索引记录的区间上，或者是锁在区间第一条之前或者是最后一条之后。举个例子，比如语句：`select c1 from t where c1 between 10 and 20 for update;`会阻止其他事物插入t.c1=15的记录。

区间锁的区间可能是单个索引值、几个索引值甚至可以为空。区间锁在某些事物隔离级别上无用，同时也是并行性和执行效率的一个牺牲点。区间锁在唯一索引上无用。例如，假设id是唯一索引，那么语句`select * from child where id = 100;`不会有区间锁，只是简单的记录锁；如果id没被索引或者是非唯一索引，那么这个语句会有区间锁。

区间锁可以被不同的事物同时持有，比如，事物A可以持有某个区间的共享区间锁，事物B也可以在相同的区间上持有独占区间锁。这中情况存在的原因是，如果一条记录被删除，那么不同事物持有的区间会合并。

区间锁只阻止其他事物对这个区间执行插入，而不会阻止其他事物获取这个相同区间的锁。

区间锁可以通过改变事物的隔离级别或者修改mysql的配置来使它失效。

#### Next-Key Locks

Next-Key锁是记录锁与区间锁的结合。

#### 插入意向锁

插入意向锁是区间锁的一种类型，在插入行之前通过Insert操作设置。这个锁释放插入的意图，如果多个事务插入到相同区间的不同位置，那么这些事务之间没必要相互等待。举个例子，假设有拥有4和7索引值的记录数据，有两个不同的事务试图分别插入索引值5和索引值6的记录，两个事务虽然会锁住4到7这个区间，但是它们相互之间并不会阻塞。

#### AUTO-INC锁

AUTO-INC锁是一种特殊的表锁，当表设置了AUTO_INCREMENT列的时候，执行插入操作的事务会持有这个锁。这个锁会使其他事务处于等待状态。

#### Predicate Locks for Spatial Indexes

这个是针对空间索引的锁。针对空间数据来说的，有兴趣请自行研究。

### 二、事务模型

在InnoDB的事务模型中，目标是把多版本数据库的最佳属性和传统的两阶段锁相结合。InnoDB默认是行级别锁和读一致性，和Oracle风格一样。InnoDB的锁信息是以节省空间的方式存储的，所以不会造成锁空间增加。比如，当多个用户锁住InnoDB表，或者表中某些行时，并不会造成内存使用增大。

#### 事务隔离级别

事务隔离是数据库处理过程的一个基础。事务隔离就是ACID中的I；当多个事务同时执行数据更新和数据查询时，当前设置的事务隔离级别可以很好的调节事务之间的执行性能、可靠性、一致性以及结果的可重复性。

InnoDB提供和支持SQL92描述的所有4种事务隔离级别：READ UNCOMMITTED，READ COMMITTED，REPEATABLE READ和SERIALIZABLE；InnoDB默认的隔离级别是：REPEATABLE READ。

用户可以改变单个数据库会话中在set transaction语句之后的数据库连接的事务隔离级别，也可以通过命令行参数--transaction-isolation或者配置文件中参数来设置整个数据库服务器的事物隔离级别。

Innodb支持每个事物隔离级别使用不同的锁策略。下面列表描述MySQL对不同事物隔离级别的支持，列表按从常用隔离级别到最少使用的顺序展示：

- REPEATABLE READ（可重复读）
  Innodb默认的隔离级别。在同一个事物中的连续读是通过读取事物里第一个读操作建立的快照实现的。这意味着如果我们在同一个事物中发出多个非锁的select语句，那么这些select语句之间是读取结果是一致的。
  而针对于锁读（比如：select...for update语句或者是lock in share mode），update和delete语句，锁的建立则依赖于执行的语句是否使用了唯一索引去检索或者是区间类型的条件。

  - 针对在唯一索引上的单个值查询，Innodb只会在找到的索引记录上加锁，不会在这个索引记录之前的区间上加锁；
  - 针对其他的条件搜索，Innodb会使用区间锁或者是next-key锁在搜索到的区间加锁，以阻止其他会话在这个区间上插入数据。

- READ COMMITTED
  每一个连续读，及时这些连续的读取操作是在同一个事物中，都是读取他们自己刷新快照后的结果。而对于锁读，update语句和delete语句，Innodb只锁索引记录，不锁区间，因此允许区间中新记录的插入。区间锁只会在外键限制检查和键值重复性检查时才会使用。
  因为区间锁被禁止了，因此在这种隔离级别下可能会出现幻读，因为其他的session可能会插入新的记录。
  如果你使用READ COMMITTED，那么你必须使用基于行的binary logging。使用READ COMMITTED的其他影响：

  - 对update和delete语句，Innodb只会持有要更新或要删除记录的锁。其他在判断where条件之后不匹配的记录的锁就会释放掉。这样很大程度的减低了死锁的发生。
  - 针对update语句，如果要更新的记录已经被锁了，那么Innodb会执行一个“半一致”读，返回其他事物提交给mysql的最新值，mysal根据这个最新值来判断是否匹配update语句的where条件。如果匹配，那么mysql会再次读取这条记录，这个时候Innodb要么就是锁住这条记录或者等待这个记录的锁。

  思考下面这个例子，假设有这样一个表：

  ```sql
  CREATE TABLE t (a INT NOT NULL, b INT) ENGINE = InnoDB;
  INSERT INTO t VALUES (1,2),(2,3),(3,2),(4,3),(5,2);
  COMMIT;
  ```

  在这个例子中，上述表格没有创建索引，所以所有查询和索引扫描都是使用隐藏的聚簇索引。
  假设一个客户端使用下面语句执行了一个update语句：

  ```sql
  SET autocommit = 0;
  UPDATE t SET b = 5 WHERE b = 3;
  ```

  紧接着上面语句执行完毕，假设有第二个客户端执行了如下update语句：

  ```sql
  SET autocommit = 0;
  UPDATE t SET b = 4 WHERE b = 2;
  ```

  当Innodb执行上面的update语句时，它首先会获取每条记录的独占锁（X锁），然后再决定是否要修改这一行记录；如果Innodb不修改这一行记录，那么Innodb会马上释放这个X锁；否则Innodb会一直持有这个锁直到事物结束。这个机制对事物过程的影响如下：
  当使用默认的隔离级别-REPEATABLE READ时，第一个客户端会获取所有记录的X锁并且不会释放它们（由于没有唯一索引，区间锁会锁住所有记录）：

  ```
  x-lock(1,2); retain x-lock
  x-lock(2,3); update(2,3) to (2,5); retain x-lock
  x-lock(3,2); retain x-lock
  x-lock(4,3); update(4,3) to (4,5); retain x-lock
  x-lock(5,2); retain x-lock
  ```

  第二个客户端的update语句在试图获取锁时被阻塞，因为第一个客户端事物还没有提交或者回滚：

  ```
  x-lock(1,2); block and wait for first UPDATE to commit or roll back
  ```

  如果使用的是READ COMMITTED的隔离级别，那么第一个客户端会获取X锁并释放那些它没有修改的行的锁：

  ```
  x-lock(1,2); unlock(1,2)
  x-lock(2,3); update(2,3) to (2,5); retain x-lock
  x-lock(3,2); unlock(3,2)
  x-lock(4,3); update(4,3) to (4,5); retain x-lock
  x-lock(5,2); unlock(5,2)
  ```

  第二个客户端的update语句，Innodb会执行“半一致”读，返回对应记录的最新提交值并去匹配where条件：

  ```
  x-lock(1,2); update(1,2) to (1,4); retain x-lock
  x-lock(2,3); unlock(2,3)
  x-lock(3,2); update(3,2) to (3,4); retain x-lock
  x-lock(4,3); unlock(4,3)
  x-lock(5,2); update(5,2) to (5,4); retain x-lock
  ```

  READ COMMITTED隔离级别的作用与被废弃的配置innodb_locks_unsafe_for_binlog一样，不同的地方如下：

  - innodb_locks_unsafe_for_binlog是一个全局设置，影响所有的session，而隔离级别可以设置为全局也可以为每个session单独设置；
  - innodb_locks_unsafe_for_binlog只能在mysql服务启动时设置生效，而隔离级别可以在启动时也可以在运行时改变。

  因此，READ COMMITTED比innodb_locks_unsafe_for_binlog提供了更好和更灵活的控制。

- READ UNCOMMITTED
  select语句以非锁状态执行，一个早期的提交值可能会被使用。因此使用这种隔离级别，会引起读不一致。这就是常说的脏读。其他方面和READ COMMITTED一样。

- SERIALIZABLE
  这个隔离级别跟REPEATABLE READ类似，但是如果autocommit是disabled，那么Innodb会隐含的将所有简单的select语句转化为select... lock in share mode。如果autocommit是enabled，那么select语句就是单独的一个事物。

#### autocommit，Commit和Rollback

