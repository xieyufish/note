## 由AOP引发的血案

**Controller类**

```java
/*
 * 文件名：[]
 * 版权：
 * 描述：
 * 修改人：shell
 * 修改时间：2016年1月4日
 * 修改内容：
 */
package com.shell.user;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;

import javax.imageio.ImageIO;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

import com.google.code.kaptcha.Producer;
import com.shell.common.constants.SysConstants;
import com.shell.common.controller.BaseController;
import com.shell.common.model.ResultObject;
import com.shell.user.impl.UserServiceImpl;

/**
 * <一句话功能简述>
 * <功能详细描述>
 * @author   shell
 * @version  [版本号,2016年1月4日]
 * @seee      
 * @since
 * @Deprecated
 */
@Controller
@Scope("prototype")
@RequestMapping("/user")
public class UserController extends BaseController {
 
 @Autowired
    private Producer captchaProducer;
 
 @Autowired
 private UserServiceImpl userService;   // 直接是一个实现类
 
 @RequestMapping("/authCode")
 public void authCode() throws IOException {
  String text = captchaProducer.createText();
  BufferedImage image = captchaProducer.createImage(text);
  session.setAttribute(SysConstants.VERIFY_CODE, text);
  OutputStream out = response.getOutputStream();
  ImageIO.write(image, "png", out);
  out.flush();
  out.close();
 }
 
 @RequestMapping("/login")
 public void login() throws IOException {
  String account = request.getParameter("account");
  String password = request.getParameter("password");
  Object user = userService.getUser(account,password);
  PrintWriter out = response.getWriter();
  ResultObject resultObject = new ResultObject();
  resultObject.setData(user);
  out.write(resultObject.toString());
 }
}
```

**Service类**

```java
/*
 * 文件名：[]
 * 版权：
 * 描述：
 * 修改人：shell
 * 修改时间：2016年3月2日
 * 修改内容：
 */
package com.shell.user.impl;

import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.shell.common.cache.CacheAnnotation;
import com.shell.common.cache.CacheOperateEnum;
import com.shell.common.dao.BaseDao;
import com.shell.common.service.impl.BaseServiceImpl;
import com.shell.user.User;
import com.shell.user.ifc.UserDao;
import com.shell.user.ifc.UserService;

/**
 * <一句话功能简述>
 * <功能详细描述>
 * @author   shell
 * @version  [版本号,2016年3月2日]
 * @seee      
 * @since
 * @Deprecated
 */
@Service
public class UserServiceImpl extends BaseServiceImpl<User> {
 @Autowired
 private UserDao dao;
 
 @Override
 protected BaseDao<User> getDao() {
  return dao;
 }
 @CacheAnnotation(operate=CacheOperateEnum.SELECT)
 public Object getUser(String account, String password) {
  Map<String, Object> params = new HashMap<>();
  params.put("account", account);
  params.put("password", password);
  params.put("sqlId", "getUser");
  Object user = queryObject(params);
  System.out.println("××××××××8×××××××执行查询××××××××××××××××××");
  return user;
 }

}
```

**AOP类**

```java
/*
 * 文件名：[]
 * 版权：
 * 描述：AOP缓存控制配置--- 注解实现
 * 修改人：shell
 * 修改时间：2016年3月2日
 * 修改内容：
 */
package com.shell.common.cache;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.ehcache.EhCacheCache;
import org.springframework.cache.ehcache.EhCacheCacheManager;
import org.springframework.stereotype.Component;

import net.sf.ehcache.Ehcache;
import net.sf.ehcache.Element;

/**
 * <一句话功能简述>
 * <功能详细描述>
 * @author   shell
 * @version  [版本号,2016年3月2日]
 * @see    
 * @since
 * @Deprecated
 */
@Aspect
@Component
public class CacheInterceptor {
 @Autowired
 private EhCacheCacheManager ehcacheManager;
 
 @Around("@annotation(CacheAnnotation)")
 public Object executeCacheOperate(ProceedingJoinPoint joinPoint) throws Throwable {
  Object target = joinPoint.getTarget();
  String className = target.getClass().getName();
  String method = joinPoint.getSignature().getName();
  CacheAnnotation anno = ((MethodSignature)joinPoint.getSignature()).getMethod().getAnnotation(CacheAnnotation.class);
 
  String key = className + "." + method;
  Object result = null;
  Ehcache ehcache = ((EhCacheCache) ehcacheManager.getCache("sample")).getNativeCache();
  switch (anno.operate()) {
  case ADD:
   joinPoint.proceed();
   break;
  case DELETE:
  case UPDATE:
   if(ehcache.isKeyInCache(key)) {
    ehcache.remove(key);
   }
   joinPoint.proceed();
   break;
  case SELECT:
   if(ehcache.isKeyInCache(key)) {
    result = ehcache.get(key);
    break;
   }
   Object value = joinPoint.proceed();
   Element element = new Element(key, value);
   ehcache.put(element);
   break;
  default:
   break;
  }
 
  return result;
 }
}
```

**aop配置**

```xml
<aop:config>
  <aop:pointcut expression="execution(* com.shell..*ServiceImpl.*(..))" id="pointCut"/>
  <aop:advisor advice-ref="txAdvice" pointcut-ref="pointCut"/>
 </aop:config>
```

**springmvc配置**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<beans xmlns="http://www.springframework.org/schema/beans"
xmlns:mvc="http://www.springframework.org/schema/mvc" xmlns:context="http://www.springframework.org/schema/context"
xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:aop="http://www.springframework.org/schema/aop"
xsi:schemaLocation="http://www.springframework.org/schema/beans  
            http://www.springframework.org/schema/beans/spring-beans-3.1.xsd 
            http://www.springframework.org/schema/aop  
      	http://www.springframework.org/schema/aop/spring-aop-3.2.xsd 
            http://www.springframework.org/schema/context   
            http://www.springframework.org/schema/context/spring-context-3.1.xsd  
            http://www.springframework.org/schema/mvc  
            http://www.springframework.org/schema/mvc/spring-mvc-3.1.xsd">
  <context:component-scan base-package="com.shell.*">
  	<context:include-filter type="annotation"
		expression="org.springframework.stereotype.Controller" />
  </context:component-scan>
  <aop:aspectj-autoproxy></aop:aspectj-autoproxy><!-- 引发血案的关键地方 -->
  <!-- 视图 -->
  <bean class="org.springframework.web.servlet.view.InternalResourceViewResolver">
    <property name="prefix" value="/WEB-INF/views/" />
    <property name="suffix" value=".jsp" />
  </bean>

  <mvc:annotation-driven></mvc:annotation-driven>
  <mvc:resources location="/WEB-INF/static/" mapping="/static/**" />
  <mvc:resources location="/assets/" mapping="/assets/**" />

  <!-- 页面生成随机验证码 -->
  <bean id="captchaProducer" class="com.google.code.kaptcha.impl.DefaultKaptcha">
  	<property name="config">
  	<bean class="com.google.code.kaptcha.util.Config">
  	<constructor-arg>
      <props>
      <prop key="kaptcha.border">no</prop>
      <prop key="kaptcha.border.color">105,179,90</prop>
      <prop key="kaptcha.textproducer.font.color">red</prop>
      <prop key="kaptcha.image.width">250</prop>
      <prop key="kaptcha.textproducer.font.size">80</prop>
      <prop key="kaptcha.image.height">90</prop>
      <prop key="kaptcha.session.key">code</prop>
      <prop key="kaptcha.textproducer.char.length">4</prop>
      <prop key="kaptcha.textproducer.font.names">宋体,楷体,微软雅黑</prop>
      </props>
    </constructor-arg>
    </bean>
  	</property>
  </bean>
</beans>
```

问题描述：当我在浏览器输入项目根地址（配置会跳转到网页登陆首页-- 会访问验证码接口（即authCode路径））时，不能够正确获取到验证码， 后台报错是说@Autowired private UserServiceImpl userService;此处的UserServiceImpl不能够找到一个类型匹配的实力注入，从而导致Controllor实例化失败，造成访问不到authCode路径，所以出不来验证码。根据我此前所有的知识，我绞尽脑汁也想不清问题所在，因为我在UserServiceImpl的类上面明确加了注解@Service，而且在配置文件中也配置了扫描注解包，从我的理解查看来看是不会有任何问题的。 但是现在问题出现了。

解决方式： 根据先前已有的一个成功功能实现，我新建了一个接口UserService，并让UserServiceImpl实现它，之后再在Controllor中换成该接口，其他配置不变，再尝试访问的时候，好了，奇迹般的好了，问题解决了。然而问题是解决了，但是我还是不知道是什么原因引起的，由于时间很晚，所以问题暂时搁下。

第二天（也就是我写文章的这天），我在公司看其他问题的时候，偶然看到了一个aop 代理 proxy-target-class配置为true，然后是什么接口和类的区别时，诶，我一想跟我昨晚的问题有点类似。

然后今晚我回来看了关于AOP的问题。

在spring中，AOP（面向切面编程）是基于动态代理的方式实现的，而spring对于动态代理的方式有两种选择： jdk动态代理和cglib动态代理。

jdk动态代理是基于接口实现的，也就是说他的代理目标对象必须要实现一个或者多个接口才能被代理；

cglib是直接针对类实现代理的，没必要实现接口；

知道这两个区别，上述问题就变得明朗了。

由于我在配置文件中加了元素：

\<aop:aspectj-autoproxy>\</aop:aspectj-autoproxy> 这个元素属性的意思是，aop代理的实现方式使用jdk的方式实现相当于\<aop:aspectj-autoproxy proxy-target-class="false">\</aop:aspectj-autoproxy>这种配置方式。

由于有这个配置的存在，因为UserServiceImpl实现是没有实现UserService接口的，而这个类有要被事物，缓存，日志3个aop代理，所以造成被代理时不能正确的被代理，被实例化，所以造成Controller注入时，找不到该UserServiceImpl的实力而失败。

知道这个原因后， 我将配置\<aop:aspectj-autoproxy proxy-target-class="false">\</aop:aspectj-autoproxy>改为\<aop:aspectj-autoproxy proxy-target-class="true">\</aop:aspectj-autoproxy>（吃配置方式表示使用cglib的代理方式），再重新将UserServiceImpl的实现接口给去掉，再次加载访问，错误不再出现。

同时有网友说spring会自动根据你的实现来选择合适的代理方式，所以我试着将\<aop:aspectj-autoproxy proxy-target-class="true">\</aop:aspectj-autoproxy>这样的配置去掉，问题也可以成功解决。

\<aop:config proxy-target-class="true"/>强制spring使用cglib代理方式

\<aop:aspectj-autoproxy proxy-target-class="true">\</aop:aspectj-autoproxy>强制spring  aspectj方式实现aop时使用cglib方式    