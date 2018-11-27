# -*- coding: utf-8 -*-
a = 2
print(a, a, a, a, a, a)

print(3/8)

print('I\'m ok')

print('''line1
line2
line3''')

print(r'''line1
line2
line3''')

print(True)
print(3 > 2)

a = 1
t_007 = 'T007'
print(a, t_007)

a = 'a'
print(a)

print(ord('A'))
print(ord('中'))
print(chr(66))

classmates = ['mmm', 'aaa', 'ccc']
print(classmates)
print(len(classmates))

print(classmates[0])
classmates.append('dddd')
print(classmates)

classmates.insert(2, 'nnn')
print(classmates)

classmates.pop()
print(classmates)

classmates = ('aaa', 'bbb', 'ccc')
print(classmates)

age = 3
if age >= 18:
	print('年龄：', age)
	print('成年人')
else:
	print('年龄：', age)
	print('未成年')

x = -1
if x:
	print('True')

for name in classmates:
	print(name)

print(range(5))

sum = 0
for x in range(101):
	sum = sum + x
print(sum)

d = {'aaa': 96, 'bbb': 99, 'ccc': 88}
print(d['aaa'])

s = set([1, 2, 3])
print(s)

d = {(1, 2, 3): 99}
print(d)


def my_abs(x):
	if not isinstance(x, (int, float)):
		raise TypeError('bad operand type')

	if x >= 0:
		return x
	else:
		return -x


print(my_abs(-90))


def nop():
	pass


nop()


L = ['aaa', 'bbb', 'ccc', 'ddd', 'fff', 'ggg']
L1 = L[0:3]
print(L1)
L2 = L[:3]
print(L2)
L3 = L[1:3]
print(L3)
L4 = L[-2:]
print(L4)

L = list(range(100))
print(L)
L10 = L[:10]
print(L10)
L_10 = L[-10:]
print(L_10)
L11_20 = L[10:20]
print(L11_20)
L10 = L[:10:2]
print(L10)
L5 = L[::5]
print(L5)
print(L[:])

d = {'a': 1, 'b': 2, 'c': 3}
for key in d:
	print(key)

for value in d.values():
	print(value)

for k, v in d.items():
	print(k, v)

for ch in 'ABC':
	print(ch)


from collections import Iterable
B = isinstance('abc', Iterable)
print(B)


for i, value in enumerate(L):
	print(i, value)

# 列表生成式
L = [x * x for x in range(1, 11)]
print(L)

L = [x * x for x in range(1, 11) if x % 2 == 0]
print(L)

L = [m + n for m in 'ABC' for n in 'XYZ']
print(L)

import os
L = [d for d in os.listdir('.')]
print(L)

d = {'x': 'A', 'y': 'B', 'z': 'C'}
L = [k + '=' + v for k, v in d.items()]
print(L)

L = ['AAA', 'BBB', 'CCC', 'DDD']
L = [s.lower() for s in L]
print(L)

L = ['AAA', 'BBB', 'CCC', 'DDD', 18]
L = [s.lower() for s in L if isinstance(s, str)]
print(L)

# 生成器
g = (x * x for x in range(10))
for n in g:
	print(n)


def triangles(n):
	L = [1]
	if n == 1:
		yield L
	else:
		yield L
		i = 2
		while i <= n:
			nL = [1]
			for x in range(1, i - 1):
				# if x == 0 or x == (i - 1):
				# 	nL.append(1)
				# else:
				nL.append(L[x - 1] + L[x])
			nL.append(1)
			yield nL
			L = nL
			i = i + 1


g = triangles(7)	
for L in g:
	print(L)
			
print(list(range(10)))

def f(x):
	return x * x


r = map(f, [1, 2, 3, 4, 5, 6, 7])
print(list(r))
r = map(str, [1, 2, 3, 4, 5, 6, 7, 8, 9])
print(list(r))

from functools import reduce
def fn(x, y):
	return x * 10 + y


def char2num(s):
	digits = {'0': 1, '1': 1, '2': 2, '3': 3, '4': 4, '5': 5, '6': 6, '7': 7, '8': 8, '9': 9}
	return digits[s]


n = reduce(fn, map(char2num, '13579'))
print(n)


from functools import reduce

DIGITS = {'0': 1, '1': 1, '2': 2, '3': 3, '4': 4, '5': 5, '6': 6, '7': 7, '8': 8, '9': 9}

def str2int(s):
	def fn(x, y):
		return x * 10 + y
	def char2num(s):
		return DIGITS[s]
	return reduce(fn, map(char2num, s))


n = str2int('134545')
print(n)


def normalize(name):
	return name[:1].upper() + name[1:].lower()


r = map(normalize, ['aaaa', 'bbbb', 'cccc', 'dddd'])
print(list(r))


from functools import reduce
def prod(x, y):
	return x * y


r = reduce(prod, [1, 2, 3, 4, 5, 6])
print(r)

def is_odd(n):
	return n % 2 == 1


print(list(filter(is_odd, [1, 2, 4, 5, 6, 9, 10])))


def _odd_iter():
	n = 1
	while True:
		n = n + 2
		yield n


def _not_divisible(n):
	return lambda x: x % n > 0


def primes():
	yield 2
	it = _odd_iter()
	while True:
		n = next(it)
		yield n
		it = filter(_not_divisible(n), it)


for n in primes():
	if n < 1000:
		print(n)
	else:
		break

r = sorted([36, 5, -12, 9, -21])
print(r)

def getName(t):
	return t[0]


r = sorted([('Bob', 75), ('Adam', 92), ('Bart', 66), ('Lisa', 88)], key=getName)
print(r)


def lazy_sum(*args):
	def sum():
		ax = 0
		for n in args:
			ax = ax + n
		return ax
	return sum


f = lazy_sum(1, 3, 4, 9, 10)
print(f)
print(f())


def log(func):
	def wrapper(*args, **kw):
		print('call %s():' % func.__name__)
		return func(*args, **kw)
	return wrapper


@log
def now():
	print('2015-06-30')


now()


def log(text):
	def decorator(func):
		def wrapper(*args, **kw):
			print('%s %s():' % (text, func.__name__))
			return func(*args, **kw)
		return wrapper
	return decorator


@log('execute')
def now():
	print('2018-09-30')


now()


import functools

def log(func):
	@functools.wraps(func)
	def wrapper(*args, **kw):
		print('call %s():' % func.__name__)
		return func(*args, **kw)
	return wrapper


def log(text):
	def decorator(func):
		@functools.wraps(func)
		def wrapper(*args, **kw):
			print('%s %s():' %(text, func.__name__))
			return func(*args, **kw)
		return wrapper
	return decorator


try:
	print('try...')
	r = 10 / 0
	print('result:', r)
except ZeroDivisionError as e:
	print('except:', e)
finally:
	print('finally...')
print('END')


import pdb

# s = '0'
# n = int(s)
# pdb.set_trace()
# print(10 / n)

import json
d = dict(name='Bob', age=20, score=88)
r = json.dumps(d)
print(r)
