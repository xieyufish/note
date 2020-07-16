#!/usr/bin/env python3
# -*- coding: utf8 -*-
__author__ = 'XieYu'

"""
requests包的学习
在urllib的学习中，处理网页验证和Cookies时，需要些Opener和handler来处理；
为了更方便的实现这些操作，就有了requests
"""

# 基本用法
import requests

r = requests.get('https://www.baidu.com/')
print(type(r))          # requests.models.Response类型
print(r.status_code)
print(type(r.text))
print(r.text)
print(r.cookies)        # RequestsCookieJar

r = requests.post('http://httpbin.org/post')
r = requests.put('http://httpbin.org/put')
r = requests.delete('http://httpbin.org/delete')
r = requests.head('http://httpbin.org/head')
r = requests.options('http://httpbin.org/get')


# GET请求
r = requests.get('http://httpbin.org/get')
print(r.text)

data = {
    'name': 'germey',
    'age': 22
}
r = requests.get('http://httpbin.org/get', params=data)
print(r.text)
print(r.json())         # 如果返回结果不是JSON格式，便会出现解析错误
print(type(r.json()))

import re
headers = {
    'User-Agent': 'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_11_4) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/52.0.2743.116 Safari/537.36'
}
r = requests.get('https://www.zhihu.com/explore', headers=headers)
pattern = re.compile('explore-feed.*?question_link.*?>(.*?)</a>', re.S)
titles = re.findall(pattern, r.text)
print(titles)

r = requests.get('https://github.com/favicon.ico')
print(r.text)
print(r.content)
with open('favicon.ico', 'wb') as f:
    f.write(r.content)


# POST请求
data = {
    'name': 'germey',
    'age': 22
}
r = requests.post('http://httpbin.org/post', data=data)
print(r.text)

# 响应
r = requests.get('http://www.jianshu.com')
print(type(r.status_code), r.status_code)
print(type(r.headers), r.headers)
print(type(r.cookies), r.cookies)
print(type(r.url), r.url)
print(type(r.history), r.history)
exit() if not r.status_code == requests.codes.ok else print('Request Successfully')

# 高级用法：文件上传、cookies设置、代理设置等
## 文件上传
files = {
    'file': open('favicon.ico', 'rb')
}
r = requests.post('http://httpbin.org/post', files=files)
print(r.text)

## cookies
r = requests.get('https://www.baidu.com')
print(r.cookies)
for key, value in r.cookies.items():
    print(key + '=' + value)

### 使用cookie-1
headers = {
    'Cookie': 'BDORZ=27315'
}
r = requests.get('https://www.baidu.com', headers=headers)
print(r.text)

### 使用cookie-2
cookies = r.cookies
jar = requests.cookies.RequestsCookieJar()
headers = {
    'Host': 'www.baidu.com'
}
for key, value in cookies.items():
    jar.set(key, value)
r = requests.get('https://www.baidu.com', cookies=jar, headers=headers)
print(r.text)

## 会话维持
requests.get('http://httpbin.org/cookies/set/number/123456789')
r = requests.get('http://httpbin.org/cookies')
print(r.text)

s = requests.Session()
s.get('http://httpbin.org/cookies/set/number/123456789')
r = s.get('http://httpbin.org/cookies')
print(r.text)

## SSL证书验证

## 代理设置
proxies = {
    'http': 'http://10.10.1.10:3128',       # 代理需要用户名密码验证是的格式：http://user:password@host:port
    'https': 'http://10.10.1.10:1080'
}
requests.get('https://www.taobao.com', proxies=proxies)
# 针对socks协议的代理处理：
# 1. 安装socks这个库：pip3 install 'requests[socks]
# proxies = {
#     'http': 'socks5://user:password@host:port'
# }
# requests.get('https://www.taobao.com', proxies=proxies)

## 超时设置: 单位为秒
r = requests.get('https://www.taobao.com', timeout=1)
print(r.status_code)

## 身份认证
from requests.auth import HTTPBasicAuth
r = requests.get('http://localhost:8080/manager', auth=HTTPBasicAuth('username', 'password'))
print(r.status_code)
# 简写
r = requests.get('http://localhost:8080/manager', auth=('username', 'password'))
print(r.status_code)

### 使用OAuth1认证的方法: pip3 install request_oauthlib
from requests_oauthlib import OAuth1
url = 'https://api.twitter.com/1.1/account/verify_credentials.json'
auth = OAuth1('YOUR_APP_KEY', 'YOUR_APP_SECRET', 'USER_OAUTH_TOKEN', 'USER_OAUTH_TOKEN_SECRET')
requests.get(url, auth=auth)

## Prepared Request
from requests import Request, Session

url = 'http://httpbin.org/post'
data = {
    'name': 'germey'
}
headers = {
    'User-Agent': 'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_11_4) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/52.0.2743.116 Safari/537.36'
}
s = Session()
req = Request('POST', url, data=data, headers=headers)
prepped = s.prepare_request(req)
r = s.send(prepped)
print(r.text)