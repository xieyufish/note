package com.shell.spi_test;

import java.util.ServiceLoader;

import com.shell.spi.Spi;

/**
 * <b>类作用描述：</b><br>
 * <p>
 * 
 * </p>
 * <br><b>创建者：</b>  	xieyu
 * <br><b>所属项目：</b>	spi_test
 * <br><b>创建日期：</b>	2017年04月25日 19:12:49
 * <br><b>修订记录：</b>	
 * <br><b>当前版本：</b>	1.0.0
 * <br><b>参考：<b>		
 */
public class SpiTest {
	public static void main(String[] args) {
		ServiceLoader<Spi> serviceLoader=ServiceLoader.load(Spi.class); 
		int i = 0;
        for(Spi spi:serviceLoader){  
        	i ++;
            spi.execute();  
        }  
        System.out.println(i);
	}
}
