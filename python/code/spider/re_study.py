#!/usr/bin/env python3
# -*- coding: utf8 -*-
__author__ = 'XieYu'

"""
re库：
正则表达式的学习
"""

# match():检测正则表达式是否匹配字符串，从字符串的开头开始匹配
import re

content = 'Hello 123 4567 World_This is a Regex Demo'
print(len(content))
result = re.match('^Hello\s\d\d\d\s\d{4}\s\w{10}', content)
print(result)
print(result.group())
print(result.span())

## 匹配目标
content = 'Hello 1234567 World_This is a Regex Demo'
result = re.match('^Hello\s(\d+)\sWorld', content)
print(result)
print(result.group())
print(result.group(1))
print(result.span())

## 通用匹配：.*匹配全部字符
content = 'Hello 123 4567 World_This is a Regex Demo'
result = re.match('^Hello.*Demo$', content)
print(result)
print(result.group())
print(result.span())

## 贪婪与非贪婪
### 贪婪匹配：匹配尽可能多的字符
content = 'Hello 1234567 World_This is a Regex Demo'
result = re.match('^He.*(\d+).*Demo$', content)
print(result)
print(result.group(1))
### 非贪婪匹配： 尽可能匹配少的字符
result = re.match('^He.*?(\d+).*Demo$', content)
print(result)
print(result.group(1))

### 贪婪与非贪婪匹配结果在字符串的结尾时的情况
content = 'http://weibo.com/comment/kEraCN'
result1 = re.match('http.*?comment/(.*?)', content)
result2 = re.match('http.*?comment/(.*)', content)
print('result1', result1.group(1))
print('result2', result2.group(1))

## 修饰符
content = '''Hello 1234567 World_This
is a Regex Demo
'''
result = re.match('^He.*?(\d+).*?Demo$', content)
print(result.group(1))
result = re.match('^He.*?(\d+).*?Demo$', content, re.S)     # re.S修饰符的作用：使.(点号)匹配包括换行符在内的所有字符；此修饰符在网页匹配中常用
print(result.group(1))

## 转义匹配
content = '(百度)www.baidu.com'
result = re.match('\(百度\)www\.baidu\.com', content)
print(result)
result = re.match('^\(.*com$', content)
print(result.group())


# search()：在匹配时会扫描整个字符串，然后返回第一个成功匹配的结果
content = 'Extra strings Hello 1234567 World_This is a Regex Demo Extra strings'
result = re.match('Hello.*?(\d+).*?Demo', content)
print(result)
result = re.search('Hello.*?(\d+).*?Demo', content)
print(result)
print(result.group())
print(result.group(1))

html = '''<div id="songs-list">
<h2 class="title">经典老歌</h2>
<p class="introdution">
经典老歌列表
</p>
<ul id="list" class="list-group">
<li data-view="2">一路上有你</li>
<li data-view="7">
<a href="/2.mp3" singer="任贤齐">沧海一声笑</a>
</li>
<li data-view="4" class="active">
<a href="/3.mp3" singer="齐秦">往事随风</a>
</li>
<li data-view="6"><a href="/4.mp3" singer="beyond">光辉岁月</a></li>
<li data-view="5"><a href="/5.mp3" singer="陈慧琳">记事本</a></li>
<li data-view="5">
<a href="/6.mp3" singer="邓丽君">但愿人长久</a>
</li>
</ul>
</div>
'''
result = re.search('<li.*?active.*?singer="(.*?)">(.*?)</a>', html, re.S)
if result:
    print(result.group(), result.group(1), result.group(2))

result = re.search('<li.*?singer="(.*?)">(.*?)</a>', html, re.S)
if result:
    print(result.group(), result.group(1), result.group(2))

result = re.search('<li.*?singer="(.*?)">(.*?)</a>', html)
if result:
    print(result.group(), result.group(1), result.group(2))


# findall()：搜索整个字符串，然后返回匹配正则表达式的所有内容
results = re.findall('<li.*?href="(.*?)".*?singer="(.*?)">(.*?)</a>', html, re.S)
print(results)
print(type(results))
results = re.findall('<li.*?>\s*?(<a.*?>)?(\w+)(</a>)?\s*?</li>', html, re.S)
for result in results:
    print(result)

# sub()：把一串文本中的所有指定字符替换
content = '54aK54yr5oiR54ix5L2g'
content = re.sub('\d+', '', content)
print(content)
html1 = re.sub('<a.*?>|</a>', '', html)
print(html1)
results = re.findall('<li.*?>(.*?)</li>', html1, re.S)
for result in results:
    print(result.strip())

# compile()：将正则字符串编译成正则表达式对象，以便在后面的匹配中复用
content1 = '2016-12-15 12:00'
content2 = '2016-12-17 12:55'
content3 = '2016-12-22 13:21'
pattern = re.compile('\d{2}:\d{2}')
result1 = re.sub(pattern, '', content1)
result2 = re.sub(pattern, '', content2)
result3 = re.sub(pattern, '', content3)
print(result1, result2, result3)