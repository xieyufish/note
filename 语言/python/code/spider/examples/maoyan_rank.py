#!/usr/bin/env python3
# -*- coding: utf-8 -*-
import requests
import re
import json
from requests.exceptions import RequestException
import time

'''
利用requests库来抓取猫眼电影TOP100的电影名称、上映时间、评分、图片等信息
'''

__author__ = 'XieYu'


def get_one_page(url):
    """
    获取指定url的网页数据
    :param url: 要抓取的网页url地址
    :return: 抓取的网页内容
    """
    try:
        headers = {
            'User-Agent': 'Mozilla/5.0 (Windows NT 6.1; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/70.0.3538.110 Safari/537.36'
        }
        response = requests.get(url, headers=headers)
        if response.status_code == requests.codes.ok:
            return response.text
        return None
    except RequestException:
        return None


def parse_one_page(html):
    """
    解析传入的网页内容
    :param html: 网页内容
    :return: 解析结果组成的生成器
    """
    pattern = re.compile('<dd>.*?board-index.*?>(.*?)</i>.*?data-src="(.*?)".*?name.*?a.*?>(.*?)</a>.*?star.*?>(.*?)</p>.*?releasetime.*?>(.*?)</p>.*?integer.*?>(.*?)</i>.*?fraction.*?>(.*?)</i>.*?</dd>', re.S)
    items = re.findall(pattern, html)
    for item in items:
        yield {
            'index': item[0],
            'image': item[1],
            'title': item[2].strip(),
            'actor': item[3].strip()[3:] if len(item[3]) > 3 else '',
            'time': item[4].strip()[5:] if len(item[4]) > 5 else '',
            'score': item[5].strip() + item[6].strip()
        }


def write_to_file(content):
    """
    将指定内容以json格式添加到文本文件result.txt中
    :param content: 待写入内容
    :return: void
    """
    with open('result.txt', 'a', encoding='utf-8') as f:
        f.write(json.dumps(content, ensure_ascii=False) + '\n')


def main(offset):
    """
    主函数，完成抓取数据的功能
    :param offset:
    :return:
    """
    url = 'http://maoyan.com/board/4?offset=' + str(offset)
    html = get_one_page(url)
    for item in parse_one_page(html):
        write_to_file(item)

if __name__ == '__main__':
    for i in range(10):
        main(offset=i * 10)
        time.sleep(1)
