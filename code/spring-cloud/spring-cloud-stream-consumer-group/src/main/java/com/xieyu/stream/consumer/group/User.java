package com.xieyu.stream.consumer.group;

/**
 * <b>类作用描述：</b>
 * 
 * <pre>
 * 
 * </pre>
 * 
 * <b>创建者：</b> xieyu <br>
 * <b>所属项目：</b> spring-cloud-stream-consumer-group <br>
 * <b>创建日期：</b> 2019年03月25日 19:09:18 <br>
 * <b>修订记录：</b> <br>
 * <b>当前版本：</b> 1.0.0 <br>
 * <b>参考：</b>
 */
public class User {

	private String name;
	private int age;

	/**
	 * xieyu
	 * 
	 * @return
	 */
	public String getName() {
		return this.name;
	}

	/**
	 * xieyu
	 * 
	 * @param
	 */
	public void setName(String name) {
		this.name = name;
	}

	/**
	 * xieyu
	 * 
	 * @return
	 */
	public int getAge() {
		return this.age;
	}

	/**
	 * xieyu
	 * 
	 * @param
	 */
	public void setAge(int age) {
		this.age = age;
	}

}
