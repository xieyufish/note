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
支持协议：file、ftp、gopher、hdl、http、https
"""