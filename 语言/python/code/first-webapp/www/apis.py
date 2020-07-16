#!/usr/bin/env python3
# -*- coding: utf-8 -*-

__author__ = 'XieYu'
'''
JSON API definition
'''

import json, logging, inspect, functools


class APIError(Exception):
    '''
	the base APIError which contains error(required), data(optional) and message(optional)
	'''

    def __init__(self, error, data='', message=''):
        super(APIError, self).__init__(message)
        self.error = error
        self.data = data
        self.message = message


class APIValueError(APIError):
    '''
	Indicate the input value has error or invalid. The data specified the error field of input form
	'''

    def __init__(self, field, message=''):
        super(APIValueError, self).__init__('value:invalid', field, message)


class APIResourceNotFountError(APIError):
    '''
	Indicate the resource war not found. The data specified the resource name.
	'''

    def __init__(self, field, message=''):
        super(APIResourceNotFountError, self).__init__('value:notfound', field,
                                                       message)


class APIPermissionError(APIError):
    '''
	Indicate the api was no permission
	'''

    def __init__(self, message=''):
        super(APIPermissionError, self).__init__('permission:forbidden',
                                                 'permission', message)
