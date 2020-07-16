#!/usr/bin/env python3
# -*- coding: utf-8 -*-

__author__ = 'XieYu'
"""
xpath解析库的使用学习
"""

from lxml import etree

text = '''
<div>
<ul>
<li class="item-0"><a href="link1.html">first item</a></li>
<li class="item-1"><a href="link2.html">second item</a></li>
<li class="item-inactive"><a href="link3.html">third item</a></li>
<li class="item-1"><a href="link4.html">fourth item</a></li>
<li class="item-0"><a href="link5.html">fifth item</a>
</ul>
</div>
'''

html = etree.HTML(text)
result = etree.tostring(html)
print(result.decode('utf-8'))

# 通过文件
# html = etree.parse('./test.html', etree.HTMLParser())
# result = etree.tostring(html)
# print(result.decode('utf-8'))

# 所有节点：//用于获取子孙节点
result = html.xpath('//*')
print(result)

# 所有li节点
result = html.xpath('//li')
print(result)
print(result[0])

# ul节点下面的所有子孙a节点
result = html.xpath('//ul//a')
print(result)

# 父节点: ..获取指定节点的父节点
result = html.xpath('//a[@href="link4.html"]/../@class')
print(result)

# parent::也可以用来获取父节点
result = html.xpath('//a[@href="link4.html"]/parent::*/@class')
print(result)

# 属性匹配：@符号进行属性过滤
result = html.xpath('//li[@class="item-0"]')
print(result)

# 文本获取: text()方法获取节点中的文本
result = html.xpath('//li[@class="item-0"]/text()')
print(result)

result = html.xpath('//li[@class="item-0"]/a/text()')
print(result)

result = html.xpath('//li[@class="item-0"]//text()')
print(result)

# 属性获取
result = html.xpath('//li/a/@href')
print(result)

# 属性多值匹配
text = '''
<li class="li li-first"><a href="link.html">first item</a></li>
'''
html1 = etree.HTML(text)
result = html1.xpath('//li[@class="li"]/a/text()')
print(result)

result = html1.xpath('//li[contains(@class, "li")]/a/text()')
print(result)

# 多属性匹配
text = '''
<li class="li li-first" name="item"><a href="link.html">first item</a></li>
'''
html2 = etree.HTML(text)
result = html2.xpath('//li[contains(@class, "li") and @name="item"]/a/text()')
print(result)

# 排序选择
result = html.xpath('//li[1]/a/text()')
print(result)
result = html.xpath('//li[last()]/a/text()')
print(result)
result = html.xpath('//li[position()<3]/a/text()')
print(result)
result = html.xpath('//li[last()-2]/a/text()')
print(result)

# 节点轴选择
result = html.xpath('//li[1]/ancestor::*')
print(result)
result = html.xpath('//li[1]/ancestor::div')
print(result)
result = html.xpath('//li[1]/attribute::*')
print(result)
result = html.xpath('//li[1]/child::a[@href="link1.html"]')
print(result)
result = html.xpath('//li[1]/descendant::span')
print(result)
result = html.xpath('//li[1]/following::*[2]')
print(result)
result = html.xpath('//li[1]/following-sibling::*')
print(result)
