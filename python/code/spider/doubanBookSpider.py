#!/usr/bin/env python3
#-*- coding: utf-8 -*-

__author__ = 'XieYu'

'抓取豆瓣网站上的书籍信息'

import sys
import time
import urllib
import urllib2
import requests
import numpy as np
from bs4 import BeautifulSoup
from openpyxl import Workbook

reload(sys)
sys.setdefaultencoding('utf8')

hds = [
	{'User-Agent': 'Mozilla/5.0 (Windows; U; Windows NT 6.1; en-US; rv:1.9.1.6) Gecko/20091201 Firefox/3.5.6'},
	{'User-Agent': 'Mozilla/5.0 (Windows NT 6.2) AppleWebkit/535.11 (KHTML, like Gecko) Chrome/17.0.963.12 Safari/535.11'},
	{'User-Agent': 'Mozilla/5.0 (compatible; MSIE 10.0; Windows NT 6.2; Trident/6.0)'}
]

