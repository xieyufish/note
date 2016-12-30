## spring aop中关于切入点pointcut中的表达式

表达式的语法:

```java
execution（modifiers-pattern? ret-type-pattern declaring-type-pattern? name-pattern（param-pattern）throws-pattern?）
```

?表示最多出现一次,  从表达式规则可以知道: 

```json
modifiers-pattern: 方法修饰符是可选的
ret-type-pattern: 方法返回类型必选
declaring-type-pattern: 类型声明(T, E之类的泛型类型声明)也是可选的
name-pattern: 方法名字必选
param-pattern: 参数名字必选
throws-pattern: 异常类型可选
```

你会使用的最频繁的返回类型模式是\*，它代表了匹配任意的返回类型,一个全限定的类型名将只会匹配返回给定类型的方法。名字模式匹配的是方法名。 你可以使用\*通配符作为所有或者部分命名模式。

参数模式稍微有点复杂：()匹配了一个不接受任何参数的方法， 而(..)匹配了一个接受任意数量参数的方法（零或者更多）。 模式(\*)匹配了一个接受一个任何类型的参数的方法。 模式(\*,String)匹配了一个接受两个参数的方法，第一个可以是任意类型， 第二个则必须是String类型。更多的信息请参阅AspectJ编程指南中 [语言语义](http://www.eclipse.org/aspectj/doc/released/progguide/semantics-pointcuts.html)的部分。

下面给出一些常用的切入点表达式例子:

1. 任意公共方法的执行:
   execution(public * *(..))

2. 任何一个以set开头的方法的执行:

   execution(* set*(..))

3. AccountService接口定义的任意方法的执行：

   execution(* com.xyz.service.AccountService.*(..))

4. 在service包中定义的任意方法的执行：

   execution(* com.xyz.service.*.*(..))
5. 在service包或其子包中定义的任意方法的执行：
   execution(* com.xyz.service..*.*(..))
6. 在service包中的任意连接点:(相当于在service包中任意方法的执行)
   within(com.xyz.service.*)
7. 在service包或其子包中的任意连接点
   within（com.xyz.service..*）
8. 实现了AccountService接口的代理对象的任意连接点
   this（com.xyz.service.AccountService）
9. 实现AccountService接口的目标对象的任意连接点 
   target（com.xyz.service.AccountService）
10. 任何一个只接受一个参数，并且运行时所传入的参数是Serializable 接口的连接点:
   args（java.io.Serializable）
11. 目标对象中有一个 @Transactional 注解的任意连接点
   @target（org.springframework.transaction.annotation.Transactional）
12. 任何一个目标对象声明的类型有一个 @Transactional 注解的连接点
   @within（org.springframework.transaction.annotation.Transactional）
13. 任何一个执行的方法有一个 @Transactional 注解的连接点 
   @annotation（org.springframework.transaction.annotation.Transactional）
14. 任何一个只接受一个参数，并且运行时所传入的参数类型具有@Classified 注解的连接点
   @args（com.xyz.security.Classified）
15. 任何一个在名为'tradeService'的Spring bean之上的连接点
   bean（tradeService）
16. 任何一个在名字匹配通配符表达式'*Service'的Spring bean之上的连接点
   bean（*Service）