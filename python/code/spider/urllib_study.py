#!/usr/bin/env python3
# -*- coding: utf-8 -*-

__author__ = 'XieYu'

'''
python3中基本库urllib的使用
'''

from urllib import request, parse

response = request.urlopen('https://www.python.org')
print(type(response))		# <class 'http.client.HTTPResponse'>

print(response.status)		# https请求的响应状态结果码
print(response.getheaders())		# 所有的请求响应头
print(response.getheader('Server'))		# Server头信息

# data参数的作用，如果不传data参数，是get方式的请求，如果有data参数则是post方式的请求
data = bytes(parse.urlencode({'word': 'hello'}), encoding='utf8')
response = request.urlopen('http://httpbin.org/post', data=data)
print(response.read())

# timeout参数
# 设置超时时间，单位为秒，如果请求超出了设置的这个时间，还没有得到响应，就抛出异常
response = request.urlopen('http://httpbin.org/get', timeout=1)
print(response.read())

import socket
import urllib.error
try:
	response = request.urlopen('http://httpbin.org/get', timeout=0.1)
except urllib.error.URLError as e:
	if isinstance(e.reason, socket.timeout):
		print('TIME OUT')


# 其他参数
# content: ssl.SSLContext类型，指定SSL设置
# cafile和capath：指定CA证书和路径
# cadefault：弃用

"""
Request类：更强大的请求类
如果请求中需要加入Headers等信息，可以用其构建
"""

req = request.Request('https://python.org')
response = request.urlopen(req)
print(response.read().decode('utf-8'))

url = 'http://httpbin.org/post'
headers = {
	'User-Agent': 'Mozilla/4.0 (compatible; MSIE 5.5; Windows NT)',
	'Host': 'httpbin.org'
}
dict = {
	'name': 'Germey'
}
data = bytes(parse.urlencode(dict), encoding='utf8')
req = request.Request(url=url, data=data, headers=headers, method='POST')
response = request.urlopen(req)
print(response.read().decode('utf-8'))


"""
高级用法：Handler
各种处理器，有专门处理登录验证的，有处理cookies的，有处理代理设置的，等等
"""

# 验证
# 针对那些提示需要输入用户名和密码验证成功才能查看的页面
from urllib.request import HTTPPasswordMgrWithDefaultRealm, HTTPBasicAuthHandler, build_opener
from urllib.error import URLError

username = 'tomcat'
password = 'tomcat'
url = 'http://localhost:8080/manager'

p = HTTPPasswordMgrWithDefaultRealm()
p.add_password(None, url, username, password)
auth_handler = HTTPBasicAuthHandler(p)
opener = build_opener(auth_handler)

try:
	result = opener.open(url)
	html = result.read().decode('utf-8')
	print(html)
except URLError as e:
	print(e.reason)


# 代理
from urllib.request import ProxyHandler

proxy_handler = ProxyHandler({
	'http': 'http://127.0.0.1:9743',
	'https': 'https://127.0.0.1:9743'
})
opener = build_opener(proxy_handler)

try:
	response = opener.open('https://www.baidu.com')
	print(response.read().decode('utf-8'))
except URLError as e:
	print(e.reason)


# Cookies
# 获取网站的Cookies
import http.cookiejar

cookie = http.cookiejar.CookieJar()
handler = request.HTTPCookieProcessor(cookie)
opener = build_opener(handler)
response = opener.open('http://www.baidu.com')
for item in cookie:
	print(item.name + "=" + item.value)


# 将cookies信息保存到文件中
filename = 'cookies.txt'
cookie = http.cookiejar.MozillaCookieJar(filename)		# 生成mozilla格式的cookies文件，用LWPCookieJar可以生成LWP格式的cookies文件
handler = request.HTTPCookieProcessor(cookie)
opener = build_opener(handler)
response = opener.open('http://www.baidu.com')
cookie.save(ignore_discard=True, ignore_expires=True)

# 使用cookie
cookie = http.cookiejar.MozillaCookieJar()
cookie.load(filename=filename, ignore_discard=True, ignore_expires=True)
for item in cookie:
	print(item.name + "=" + item.value)

handler = request.HTTPCookieProcessor(cookie)
opener = build_opener(handler)
response = opener.open('http://www.baidu.com')
print(response.read().decode('utf-8'))


"""
异常处理：
urllib中的error模块定义了由request模块产生的异常
"""

# URLError：error异常模块的基类，request模块产生的异常都可以通过捕获这个类的实例来处理
# 属性：reason，返回错误原因

try:
	response = request.urlopen('https://cuiqingcai.com/index.htm')
except urllib.error.URLError as e:
	print(e.reason)

# HTTPError：URLError的子类，处理HTTP请求错误，比如认证请求失败等
# code：返回HTTP状态码
# reason：返回错误原因
# headers：返回请求头

try:
	response = request.urlopen('https://cuiqingcai.com/index.htm')
except urllib.error.HTTPError as e:
	print(e.reason, e.code, e.headers, sep='\n')

# reason属性返回的不一定是字符串，也可能是一个对象
try:
	response = request.urlopen('https://www.baidu.com', timeout=0.01)
except urllib.error.URLError as e:
	print(type(e.reason))
	if isinstance(e.reason, socket.timeout):
		print('TIME OUT')


"""
解析链接-parse模块
定义了处理URL的标准接口，例如实现URL各部分的抽取、合并以及链接转换
支持协议：file、ftp、gopher、hdl、http、https等
"""
from urllib.parse import urlparse

# 1. urlparse()的使用
# urltring：必选项，待解析的URL
result = urlparse('http://www.baidu.com/index.html;user?id=5#comment')
print(type(result), result)

# scheme：默认协议，假如这个链接没有带协议信息，会将这个作为默认的协议
result = urlparse('www.baidu.com/index.html;user?id=5#comment', scheme='https')
print(result)

# allow_fragments：是否忽略fragment，及锚点
result = urlparse('http://www.baidu.com/index.html;user?id=5#comment', allow_fragments=False)
print(result)

# urlparse()返回对象ParseResult实际上是一个元组，可以用索引顺序来获取，也可以用属性名获取
result = urlparse('http://www.baidu.com/index.html#comment', allow_fragments=False)
print(result.scheme, result[0], result.netloc, result[1], sep='\n')


# 2. urlunparse()的使用：接受的参数是一个可迭代对象，但是它的长度必须是6，否则会抛出异常
from urllib.parse import urlunparse
data = ['http', 'www.baidu.com', 'index.html', 'user', 'a=b', 'comment']
print(urlunparse(data))

# 3. urlsplit()：和urlparse()类似，只不过它不再单独解析params这一部分，只返回5个结果
from urllib.parse import urlsplit
result = urlsplit('http://www.baidu.com/index.html;user?id=5#comment')
print(result)

# 4. urlunsplit()：和urlunparse()类似，接受的参数是一个可迭代对象，但是它的长度必须是5
from urllib.parse import urlunsplit
data = ['http', 'www.baidu.com', 'index.html', 'a=6', 'comment']
print(urlunsplit(data))

# 5. urljoin()：提供一个base_url作为第一个参数(基础链接)，将新的链接作为第二个参数，该方法会分析base_url的scheme、netloc和path这
#    些内容并对新链接缺失部分进行补充
from urllib.parse import urljoin
print(urljoin('http://www.baidu.com', 'FAQ.html'))
print(urljoin('http://www.baidu.com', 'https://cuiqingcai.com/FAQ.html'))
print(urljoin('http://www.baidu.com/about.html', 'https://cuiqingcai.com/FAQ.html'))
print(urljoin('http://www.baidu.com/about.html', 'https://cuiqingcai.com/FAQ.html?question=2'))
print(urljoin('http://www.baidu.com?wd=abc', 'https://cuiqingcai.com/index.php'))
print(urljoin('http://www.baidu.com', '?category=2#comment'))
print(urljoin('www.baidu.com', '?category=2#comment'))
print(urljoin('www.baidu.com#comment', '?category=2'))

# 6. urlencode(): 序列化为get请求参数
from urllib.parse import urlencode
params = {
	'name': 'germey',
	'age': 22
}
base_url = 'http://www.baidu.com?'
url = base_url + urlencode(params)
print(url)

# 7. parse_qs(): 反序列化，将get请求参数转回字典
from urllib.parse import parse_qs
query = 'name=germey&age=22'
print(parse_qs(query))

# 8. parse_qsl(): 将参数转化为元组组成的列表
from urllib.parse import parse_qsl
query = 'name=germey&age=22'
print(parse_qsl(query))

# 9. quote(): 将内容转化为URL编码的格式
from urllib.parse import quote
keyword = '壁纸'
url = 'https://www.baidu.com/s?wd=' + quote(keyword)
print(url)

# 10. unquote(): 进行URL解码
from urllib.parse import unquote
url = 'https://www.baidu.com/s?wd=%E5%A3%81%E7%BA%B8'
print(unquote(url))