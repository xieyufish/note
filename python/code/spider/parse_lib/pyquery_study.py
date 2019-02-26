#!/usr/bin/evn python3
# -*- coding: utf-8 -*-
__author__ = 'XieYu'

'''
pyquery库的学习使用:类似jquery里面一样，可以用类似的css选择器或者方法去获取节点
'''
from pyquery import PyQuery as pq

text = '''
<div id="container">
<ul class="list">
<li class="item-0">first item</li>
<li class="item-1"><a href="link2.html">second item</a></li>
<li class="item-0 active"><a href="link3.html"><span class="bold">third item</span></a></li>
<li class="item-1 active"><a href="link4.html">fourth item</a></li>
<li class="item-0"><a href="link5.html">fifth item</a>
</ul>
</div>
'''

# 字符串初始化
doc = pq(text)
print(doc('li'))

# URL初始化
doc = pq(url='https://cuiqingcai.com')
print(doc('title'))

# 文件初始化
doc = pq(filename='demo.html')
print(doc('li'))

# 基本CSS选择器
print(doc('#container .list li'))
print(type(doc('#container .list li')))

# 查找节点
items = doc('.list')
print(type(items))
print(items)
lis = items.find('li')
print(type(lis))
print(lis)
