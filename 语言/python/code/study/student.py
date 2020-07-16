#!/usr/bin/env python3
# -*- coding: utf-8 -*-

'a test class'

__author__ = 'XieYu'


class Student(object):
	
	count = 0  # 类变量

	__slots__ = ('__name', '__score')		# 限制Student类只能有这两个属性

	def __init__(self, name, score):
		self.__name = name
		self.__score = score

	def print_score(self):
		print('%s: %s' % (self.__name, self.__score))

	def get_name(self):
		return self.__name

	def get_score(self):
		return self.__score

	


xieyu = Student('xieyu', '100')
xieyu.print_score()
# print(xieyu.__score)

print(type(xieyu))
print(dir(xieyu))

import json

def student2dict(std):
	return {
		'name': std.get_name(),
		'score': std.get_score()
	}


print(json.dumps(xieyu, default=student2dict))
# print(json.dumps(xieyu, default=lambda obj: obj.__dict__))


def dict2student(d):
	return Student(d['name'], d['score'])

json_str = '{"name": "xy", "score": 99}'
print(json.loads(json_str, object_hook=dict2student))