## Cookie详解

Cookie，指某些网站为了辨别用户身份、进行session管理跟踪而存储在用户本地终端上的数据。cookie最初是由网景公司开发的，之后定义域标准RFC2109和2965文档中，最新取代的规范是RFC6265。现在所有主流浏览器都支持它。

下面我们来详细的介绍一下cookie。

### 1. cookie的类型

可以将cookie分为两种类型：会话cookie和持久cookie。会话cookie是一种临时cookie，它记录了用户访问站点时的设置和偏好，当用户退出浏览器时，会话cookie就被删除；持久cookie的生存时间更长久，它们存储在硬盘上，浏览器退出，计算机重启它们依然存在。我们可以很直观的感受这两种cookie的区别就在于过期时间的差异上。

### 2. cookie是如何工作的

当一个客户端首次访问web站点时，web站点为了标识出这个客户端，会在首次的http响应中添加Set-Cookie或者Set-Cookie2的响应头，这个响应头的值为：key=value的形式；这个响应头是为了告诉客户端你下次再访问我时请带上我返回给你的cookie值。这样在客户端下次请求同一个web站点是，会在请求中加上cookies的请求头，值为：key=value的形式。

例如，在Tomcat容器中，如果客户端首次访问Tomcat中的某个应用，tomcat会返回一个Set-Cookie：JSESSIONID=xxxxxxxx的响应头给客户端；客户端下次请求时就会在cookie里面带上JSESSIONID这个值，从而在Tomcat中可以通过这个值来跟踪会话session。

浏览器会记住从服务器返回的Set-Cookie或Set-Cookie2首部中的cookie内容，并将其存储在浏览器的cookie数据库中，等下次用户访问同一个站点时，浏览器会选中对应的cookie值放在请求首部中发送给服务器。

### 3. cookie的组成

cookie规范有两个不同的版本：cookies版本0（有时被称为Netscape cookies）和cookies版本1（RFC2965）。版本1是对版本0的扩展，现在广泛使用的是版本0。所以下面我们讨论的是版本0的cookie组成。

版本0的规范是由网景公司定义的，这个规范中的cookie定义了Set-Cookie响应首部、cookie请求首部以及用于控制cookie的字段。版本0的cookie格式如下：

Set-Cookie: name=value\[; expires=date] \[; path=path] \[; domain=domain] [; secure]

Cookie: name1=value1 [; name2=value2] ...

Set-Cookie首部有一个强制性的cookie名和cookie值，后面跟着可选的cookie属性，中间由分号分隔；下表描述了Set-Cookie的字段。

| 属性       | 描述                                                         |
| ---------- | ------------------------------------------------------------ |
| NAME=VALUE | 强制的。NAME和VALUE都是字符序列，除非包含在双引号内，否则不包括分号、<br />逗号、等号和空格。服务器可以创建任意的NAME=VALUE关联，在后继对站点的<br />访问中会将其送回给服务器 |
| Expires    | 可选的。这个属性会指定一个日期字符串，来定义cookie的实际生存时间，一旦过了这个日<br />期就不再存储或发送这个cookie。日期格式：Weekday，dd-mm-yyyy HH:mm:ss GMT<br />如果没有指定这个属性值，则此cookie为会话cookie |
| Domain     | 可选的。浏览器只向指定域中的服务器主机名发送cookie。这样服务器就将cookie限制在了<br />特定的域中。比如：acme.com域就与anvil.acme.com和shipping.acme.com相匹配，但与<br />www.cnn.com就不匹配了。如果没有指定域，就默认为产生Set-Cookie响应的服务器的主机名。<br />域规则：以.com,.edu,.net,.org,.gov,.mil,.int等特定域结尾只需要两个句号，所有其他域至少<br />需要三个句号。 |
| Path       | 可选的。通过这个属性可以为服务器上特定的路径分配cookie。如果path属性是一个URL路径前缀，就可以附加一个cookie。路径/foo与/foobar和/佛哦/bar.html相匹配。路径“/”与域名内所有内容匹配。如果没有指定路径，就将其设置为产生Set-Cookie响应的URL的路径。<br />Set-Cookie: lastorder=1111; path=/orders |
| Secure     | 可选的。如果包含了这一个属性，就只有在HTTP使用SSL安全连接时(就是https请求)才会发送cookie |

客户端发送请求时，会将所有与域、路径和安全过滤器相匹配的未过期cookie都发送给这个站点。所有cookie都被组合到一个Cookie首部中：

Cookie: JSESSIONID=xxxxxxxxx; lastorder=1111







