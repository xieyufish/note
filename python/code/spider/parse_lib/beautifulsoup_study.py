#!/usr/bin/env python3
# -*- coding: utf-8 -*-

__author__ = 'XieYu'
'''
Beautiful Soup库的学习使用
'''

from bs4 import BeautifulSoup

soup = BeautifulSoup('<p>Hello</p>', 'lxml')
print(soup.p.string)

# 基本用法
html = '''
<html><head><title>The Dormouse's story</title></head>
<body>
<p class="story">Once upon a time there were three little sisters; and their names were
<a href="http://example.com/elsie" class="sister" id="link1"><!-- Elsie --></a>,
<a href="http://example.com/lacie" class="sister" id = "link2">Lacie</a> and
<a href="http://example.com/tillie" class="sister" id="link3">Tillie</a>;
and they lived at the bottom of a well.</p>
<p class="story">...</p>
'''
soup = BeautifulSoup(html, 'lxml')
print(soup.prettify())
print(soup.title.string)

# 节点选择器
## 选择元素
print(soup.title)
print(type(soup.title))
print(soup.title.string)
print(soup.head)
print(soup.p)

## 提取信息
### 获取节点名称
print(soup.title.name)

### 获取属性
print(soup.p.attrs)
print(soup.p.attrs['name'])
print(soup.p['name'])
print(soup.p['class'])

### 获取节点元素包含的内容
print(soup.p.string)

## 嵌套选择
print(soup.head.title)
print(type(soup.head.title))
print(soup.head.title.string)

## 关联选择
### 子节点和子孙节点
print(soup.p.contents)  # 直接子节点
print(soup.p.children)  # 直接子节点的生成器类型
for i, child in enumerate(soup.p.children):
    print(i, child)

print(soup.p.descendants)   # 子孙节点
for i, child in enumerate(soup.p.descendants):
    print(i, child)

### 父节点和祖先节点
print(soup.a.parent)    # 直接父节点
print(soup.a.parents)   # 所有祖先节点
print(list(enumerate(soup.a.parents)))

### 兄弟节点
print(soup.a.next_sibling)
print(soup.a.previous_sibling)
print(list(enumerate(soup.a.next_siblings)))
print(list(enumerate(soup.a.previous_siblings)))

### 提取信息
print(soup.a.next_sibling)
print(soup.a.next_sibling.string)

## 方法选择器
### find_all(name, attrs, recursive, text, **kwargs)
#### name
print(soup.find_all(name='a'))
print(type(soup.find_all(name='a')[0]))

#### attrs
print(soup.find_all(attrs={'class': 'story'}))
print(soup.find_all(id='link1'))
print(soup.find_all(class_='story'))

#### text: 匹配节点的文本，可以是字符串也可以是正则表达式对象，返回的是匹配的文本节点的内容集合
import re
print(soup.find_all(text=re.compile('Tillie')))

### find():返回第一个匹配的元素
print(soup.find(name='a'))
print(type(soup.find(name='a')))
print(soup.find(class_='story'))

## CSS选择器-select()方法
print(soup.select('.story'))
print(soup.select('#link1'))
print(soup.select('a')[0])
