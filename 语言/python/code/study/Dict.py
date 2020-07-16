#!/usr/bin/env python3
# -*- coding: utf-8 -*-

'a to be unit tested class'

__author__ = 'XieYu'


class Dict(dict):

	def __init__(self, **kw):
		super().__init__(**kw)

	def __getattr__(self, key):
		try:
			return self[key]
		except KeyError:
			raise AttributeError(r"'Dict' object has no attribute '%s'" % key)

	def __setattr__(self, key, value):
		self[key] = value
