package com.shell.spi_provider2;

import com.shell.spi.Spi;

/**
 * <b>类作用描述：</b><br>
 * <p>
 * 
 * </p>
 * <br><b>创建者：</b>  	xieyu
 * <br><b>所属项目：</b>	spi_provider2
 * <br><b>创建日期：</b>	2017年04月25日 19:10:02
 * <br><b>修订记录：</b>	
 * <br><b>当前版本：</b>	1.0.0
 * <br><b>参考：<b>		
 */
public class SpiProviderSecond implements Spi {

	@Override
	public void execute() {
		System.out.println("SpiProviderSecond");
	}

}
