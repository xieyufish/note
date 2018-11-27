#!/usr/bin/env python3
# -*- coding: utf-8 -*-

'collectons module study'

__author__ = 'XieYu'

from collections import namedtuple

Point = namedtuple('Point', ['x', 'y'])
p = Point(1, 2)
print(p.x, p.y)

from collections import deque
q = deque(['a', 'b', 'c'])
q.append('x')
q.appendleft('y')
print(q)