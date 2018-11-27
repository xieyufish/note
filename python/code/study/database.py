#!/usr/bin/env python3
# -*- conding: utf-8 -*-

'数据库操作学习'

__author__ = 'XieYu'

import mysql.connector

conn = mysql.connector.connect(user='root', password='xieyu0229', database='python_test')
cursor = conn.cursor()

cursor.execute('create table user(id varchar(20) primary key, name varchar(20))')
cursor.execute('insert into user(id, name) values(%s, %s)', ['1', 'Xieyu'])
cursor.rowcount
conn.commit()
cursor.close()

cursor = conn.cursor()
cursor.execute('select * from user where id = %s', ('1',))
values = cursor.fetchall()
print(values)

cursor.close()

conn.close()