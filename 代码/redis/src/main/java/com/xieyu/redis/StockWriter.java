package com.xieyu.redis;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import com.alibaba.excel.ExcelReader;
import com.alibaba.excel.read.context.AnalysisContext;
import com.alibaba.excel.read.event.AnalysisEventListener;
import com.alibaba.excel.support.ExcelTypeEnum;
import com.xinxun.core.data.service.StockDataService;
import com.xinxun.core.enums.PriceType;
import com.xinxun.core.model.DayData;
import com.xinxun.core.utils.DateUtil;
import com.xinxun.core.utils.JsonUtil;

import redis.clients.jedis.Jedis;

/**
 * <b>类作用描述：</b>
 * <pre>
 * 
 * </pre>
 * <b>创建者：</b>  	xieyu <br>
 * <b>所属项目：</b>	redis <br>
 * <b>创建日期：</b>	2019年04月25日 10:33:32 <br>
 * <b>修订记录：</b>	<br>
 * <b>当前版本：</b>	1.0.0 <br>
 * <b>参考：</b>		
 */
public class StockWriter {
	
	public static void main(String[] args) throws FileNotFoundException {
		Jedis jedis = new Jedis("localhost");
//		writeToRedis(jedis);
//		getStocks(jedis, "SH600000", "2000-01-01", "2019-04-24");
//		InputStream inputStream = getInputStream();
		FileInputStream in = new FileInputStream("C:/Users/admin/Desktop/文件夹/工作文档/项目文档/账户分析平台文档/交割单数据/lb-万联证券/原始文件/20190410-20190417.xls");
		ExcelReader excelReader = new ExcelReader(in, ExcelTypeEnum.XLS, null, new AnalysisEventListener() {

			@Override
			public void invoke(Object object, AnalysisContext context) {
				System.out.println(object);
			}

			@Override
			public void doAfterAllAnalysed(AnalysisContext context) {
			}
			
		});
		excelReader.read();
	}
	
	public static void writeToRedis(Jedis jedis) {
		List<DayData> dayDatas =  StockDataService.getDayData("SH600000", DateUtil.getToday(), PriceType.EXIT_RIGHT);
		if (dayDatas != null && dayDatas.size() > 0) {
			for (DayData dayData : dayDatas) {
				System.out.println(dayData.getDate());
				jedis.zadd("date:" + dayData.getStockCode(), DateUtil.str2Long(dayData.getDate()), "dayData:" + dayData.getStockCode() + ":" + dayData.getDate());
				jedis.set("dayData:" + dayData.getStockCode() + ":" + dayData.getDate(), JsonUtil.toJson(dayData));
			}
		}
	}
	
	public static List<String> getStocks(Jedis jedis, String stockCode, String startDate, String endDate) {
		Long start = jedis.zrank("date:" + stockCode, "dayData:" + stockCode + ":" + startDate);
		Long end = jedis.zrank("date:" + stockCode, "dayData:" + stockCode + ":" + endDate);
		
		Set<String> keys = jedis.zrange("date:" + stockCode, start, end);
		List<String> results = new LinkedList<>();
		for (String key : keys) {
			String dayData = jedis.get(key);
			results.add(dayData);
		}
		return results;
	}
	
	
}
