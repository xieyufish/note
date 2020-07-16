#!/usr/bin/env python3
# -*- coding: utf-8 -*-

try:
	f = open('D:\logs\info.log', 'r', encoding='gbk', errors='ignore')
	s = f.read()
	print(s)
finally:
	if f:
		f.close()


with open('D:\logs\info.log', 'r', encoding='gbk') as f:
	print(f.read())