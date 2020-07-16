## 认识JWT

### 1. JWT是什么

**JWT全称JSON Web Token，是一个开放标准，收录于[RFC 7519](https://tools.ietf.org/html/rfc7519)；**它是一中紧凑型的JSON对象，可用于在多个参与方之间进行安全的信息传输，因为它是包含有数字签名可被验证和信任的。它的使用场景有：

- **授权认证：**这是JWT最常见的使用场景；当一个用户登录之后，在后续的每个请求中都包含一个JWT用于访问路径、服务以及资源等的权限判断；JWT在单点登录的场景中使用非常广泛，因为开销小以及其跨域使用的能力；
- **信息交流：**如果要在多个参与方中安全的传输信息，那么JWT也是一种非常好的方法；因为JWT可以被签名，例如通过使用私钥和公钥，你可以确保发送方的身份，此外，由于签名是由header和payload两部分计算得来的，你也可以验证内容是否有被篡改。

### 3. JWT的结构

JWT是由三个独立的部分组成，各个部分通过点号(.)拼装在一起组成一个字符串，这三个部分分别是：

- Header
- Payload
- Signature

因此，一个典型的JWT字符串看起来就像这样：`xxxxx.yyyyy.zzzzz`。

#### Header

**JWT串的第一个部分，包含的内容是一个JSON格式字符串，典型的组成方式是包含两个属性：typ和alg；**其中typ表示类型，一般就取JWT这个值，alg表示签名部分所使用的算法，比如HS256（HMAC SHA256）或者是RSA。举例如下：

```json
{
  "alg": "HS256",
  "typ": "JWT"
}
```

然后，将这个json字符串使用**Base64Url**进行编码即组成了JWT的第一部分。

#### Payload

JWT串的第二个部分就是Payload，也是一个JSON串，包含了一些声明内容（Claims），典型的是包含一个用户实体属性以及一些额外的信息，总体可以分为三类，分别是：

- **Registered claims：**这种属性数据虽然不是强制性规定，但是是标准规范推荐使用的预定义好的一些属性，包括：**iss**(issuer)，**exp**(expiration time)，**sub**(subject)，**aud**(audience)等；
- **Public claims：**这种属性可以由开发人员随意定义；
- **Private claims：**这种属性是多方约定好的用于共享信息的，即不属于registered也不属于public的。

这么看来，public和private之前的界限很模糊，可以这么理解，即信息交流双方约定好可能会用到的属于private，而不是约定好的由发送方随意添加的算public。

一个payload的例子如下：

```json
{
  "sub": "1234567890",
  "name": "John Doe",
  "admin": true
}
```

然后也使用**Base64Url**方式对这个json串进行编码，即组成JWT的第二个部分。

#### Signature

JWT串的第三个部分；为了创造一个签名串，你必须要利用编码后的header、编码后的payload以及一个密码，然后用指定的算法进行签名。例如你想使用RSA算法，则这个签名串将以下列方式创建：

```java
String privateKey = "私钥串";
byte[] keyBytes = (new BASE64Decoder()).decodeBuffer(privateKey);
PKCS8EncodedKeySpec pkcs8KepSpec = new PKCS8EncodedKeySpec(keyBytes);
KeyFactory keyFactory = KeyFactory.getInstance("RSA");
PrivateKey privateKey = keyFactory.generatePrivate(pkcs8KepSpec);

Signature signature = Signature.getInstance("MD5withRSA");
signature.initSign(privateKey);
signature.update(data);
(new BASE64Encoder()).encodeBuffer(signature.sign());
```

这个签名部分是用来验证JWT包含的信息没有被改变，同时如果签名是通过私钥生成的，也可以来验证发送者的身份。

#### 组成JWT

上述三部分创建好之后，将他们通过点号拼接在一起即可组成一个标准的JWT字符串。如下：

```json
eyJraWQiOiJlWGF1bm1MIiwiYWxnIjoiUlMyNTYifQ.eyJpc3MiOiJodHRwczovL2FwcGxlaWQuYXBwbGUuY29tIiwiYXVkIjoiY29tLmtpbmdkZWUuTXlNb25leVByby53YXRjaGtpdGFwcC53YXRjaGtpdGV4dGVuc2lvbiIsImV4cCI6MTU4MzQ4OTI4MSwiaWF0IjoxNTgzNDg4NjgxLCJzdWIiOiIwMDA2MTYuMzI4ZjU1YzNmMzRkNDZhMGFjMTgxZjY2Yjc5OTQ0MDAuMDk1NyIsImF0X2hhc2giOiJWcXFMMndZbEY3cThTR2lteGYxa1FnIiwiZW1haWwiOiJpdnV4ZXJ2YXJmQHByaXZhdGVyZWxheS5hcHBsZWlkLmNvbSIsImVtYWlsX3ZlcmlmaWVkIjoidHJ1ZSIsImlzX3ByaXZhdGVfZW1haWwiOiJ0cnVlIiwiYXV0aF90aW1lIjoxNTgzNDg4Njc4LCJub25jZV9zdXBwb3J0ZWQiOnRydWV9.oXs_phGBOdmbDkP-MYf503e-_fAw9Is-IagU0JoLUp4ix1FgEjH8KL9ffD69QVvkpyCpjkVmtHcWUFhr27nVrakT2c3lzEQXLChg01hr3V72VKO6Igqzt52qo_se-UeggdPXoGzuGfzjRr4Nci7auKaZ3H9XNaXaIYJxIq7VE-ydTrZ8VXaIc_ThX_v4P8F3J4QpdHzrpRdenPrABqzer8JguB4Ppe82TnJkmFR1AuxG4IUj6ShvHLix46_MCjfVYlHaPqH5PRIR4D8OjRFWYpzfGsjW4nupArxCsI1tDREKK_Hut9gYH7yacXd1nVVjhkqvwhieXRYOZrrz09B6mg
```

> 注意：虽然JWT包含有签名，可验证和检测，但是我们要清楚JWT中的header和payload部分都只是Base64Url编码，这都是可见的，所以一些密码等相关的机密信息不要放在JWT中进行传输哦

