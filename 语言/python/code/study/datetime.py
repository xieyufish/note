#!/usr/bin/env python3
# -*- coding: utf-8 -*-

'datatime module study'

__author__ = 'XieYu'

from datetime import datetime

now = datetime.now()
print(now)

dt = datetime(2018, 11, 26, 15, 47)
print(dt)

ts = dt.timestamp()
print(ts)

print(datetime.fromtimestamp(ts))
print(datetime.utcfromtimestamp(ts))

cday = datetime.strptime('2018-11-26 15:56', '%Y-%m-%d %H:%M')
print(cday)


from datetime import timedelta
now = datetime.now()
ten_hour_later = now + timedelta(hours=10)
print(ten_hour_later)

one_day_before = now - timedelta(days=1)
print(one_day_before)

tt = now + timedelta(days=2, hours=12)
print(tt)