package com.xieyu.proxy.jdk;

import junit.framework.TestCase;

/**
 * Unit test for simple App.
 */
public class TestJDKProxy 
    extends TestCase
{
    public void testJDKProxy() {
    	Fruit fruit = (Fruit) JDKProxy.proxy(Fruit.class, new Apple());
    	fruit.show();
    }
}
