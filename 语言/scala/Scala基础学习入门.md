## Scala基础学习入门

### 数据类型

| 数据类型    | 描述                                      |
| ------- | :-------------------------------------- |
| Byte    | 8位有符号, -128~127                         |
| Short   | 16位, -32768~32767                       |
| Int     | 32位有符号, -2147483648 到 2147483647        |
| Long    | 64位                                     |
| Float   | 32位                                     |
| Double  | 64位                                     |
| Char    | 16位                                     |
| String  | 字符串                                     |
| Boolean | true或false                              |
| Unit    | 无值,类似void,用做不返回任何结果的方法结果类型. 只有一个实例值: () |
| Null    | null或空引用                                |
| Nothing | Scala的类层级的最低端, 是任何其他类型的子类型              |
| Any     | 所有其他类型的超类                               |
| AnyRef  | 所有引用类的基类                                |

**Scala中没有java中的原生类型, 在Scala中是可以对数字等基础类型调用方法的**

### 变量

Scala中声明变量的方式:

- 声明变量: *var* variableName [: Type]\[ = value], Scala中可以根据值类型推断变量的类型
- 声明常量: *val* constName [: Type] = value

### 访问修饰符

​     Scala访问修饰符基本和Java一样, 分别有: **private**, **protected**, **public**; 如果没有指定访问修饰符,默认情况下,Scala对象的访问级别都是**public**.

> Scala中**private**和**protected**都比java更加严格, protected只允许子类访问, private只允许类内访问, 外层类都不能访问

**作用域保护**

Scala中，访问修饰符可以通过使用限定词强调。格式为:

```scala
private[x] 或者 protected[x]
这里的x指代某个所属的包、类或单例对象。如果写成private[x],读作"这个成员除了对[…]中的类或[…]中的包中的类及它们的伴生对像可见外，对其它所有类都是private"。
例子：
package bobsrocckets{
    package navigation{
        private[bobsrockets] class Navigator{
         protected[navigation] def useStarChart(){}
         class LegOfJourney{
             private[Navigator] val distance = 100
             }
            private[this] var speed = 200
            }
        }
        package launch{
        import navigation._
        object Vehicle{
        private[launch] val guide = new Navigator
        }
    }
}
上述例子中，类Navigator被标记为private[bobsrockets]就是说这个类对包含在bobsrockets包里的所有的类和对象可见。
比如说，从Vehicle对象里对Navigator的访问是被允许的，因为对象Vehicle包含在包launch中，而launch包在bobsrockets中，相反，所有在包bobsrockets之外的代码都不能访问类Navigator。
```

### 循环-函数

```scala
import java.util.Date


object ScalaBase {
  def main(args: Array[String]): Unit = {
  	// 变量声明方式: var variableName : DataType [= Initial Value];
    var myVar : String = "Foo";  // 变量声明和定义
    val myVal : String = "Too";  // 常量声明
    
    myVar = "ChangeFoo";
    println(myVar);
    
    /************************************循环*******************************/
    
    var x = 0;
    var i = 0;
    //  for循环的yield使用, 注意这里for紧接着的是一个花括号而不再是圆括号, 将返回一个数组集合
    println("for循环yield的使用");
    var fors = for{ i <- 0 to 10;
        x = i + 3   // 这一行不能添加逗号  x = i + 3; 将报错
    }yield x;
    
    for (i <- fors) {
    	println(i);
    }
    
    // for循环的过滤
    println("for循环的过滤");
    for (i <- fors; if i > 6; if i < 10) {
    	println(i);
    }
    
    // break语句的用法: 默认不支持break语法, 不许要引入scala.util.control._ 包
    import scala.util.control._;  // import语句可以在需要的地方引用
    
    // 创建breaks对象
    println("break的用法");
    val loop = new Breaks;
    loop.breakable(
    		for (i <- fors) {
    			if (i > 9) {
    				loop.break;
    			}
    			println(i);
    		}
    );
    
    
    /***************************************函数****************************/
    
    println("函数的使用");
    println(addInt(5, 10));  // 打印: 15
    println(addInt(5,10, 7)); // 打印: 22
    println(addInt(b=10, a=6)) // 指定函数参数名 调用函数  此时函数体中a=6,b=10
    
    println("传名函数的调用");
    delayed(time());
    
    println("可变参数函数调用");
    printStrings("Shell", "Scala", "Spark", "Java");
    
    println("默认参数值调用");
    println(addIntDefault());  // 打印: 12
    
    println("高阶函数调用");
    println(apply(layout, 10));  // 打印: [10]
    
    println("偏应用函数调用");
    var date = new Date;
    log(date, "message1");
    Thread.sleep(1000);
    log(date, "message2");
    
    val logWithDateBound = log(date, _ : String);
    logWithDateBound("message1");
    Thread.sleep(1000);
    logWithDateBound("message2");
    
    
    
    /********************************数组***********************************/
    
    println("数组使用");
    // 一维数组声明: var z : Array[Type] = new Array[Type](length);
    var z : Array[String] = new Array[String](3);
    z(0) = "Java";
    z(1) = "Scala";
    z(2) = "Shell";
    // 数组定义
    var arr = Array("Java", "Scala", "Shell", "JavaScript");
    
    // 多维数组
    import Array._;
    var myMatrix = ofDim[Int](3,3);
    
    
    /**********************************集合********************************/
    
    // 1. Scala.List定义的两种方式
    var site : List[String] = List("Runoob", "Google", "Baidu");
    var nums : List[Int] = List(1,2,3,4);
    var empty : List[Nothing] = List();
    var dim : List[List[Int]] = List(List(1,0,0), List(0,1,0), List(0,0,1));
    
    site = "Runoob" :: ("Google" :: ("Baidu" :: Nil));
    nums = 1 :: (2 :: (3 :: (4 :: Nil)));
    empty = Nil;
    dim = (1 :: (0 :: (0 :: Nil))) :: (0 :: (1 :: (0 :: Nil))) :: (0 :: (0 :: (1 :: Nil))) :: Nil;
    
    // 2. Scala.Set 分可变集合和不可变集合  默认是不可变集合
    var set = Set(1,2,3);
    
    // 3. Scala.Map 也分可变和不可变, 默认为不可变map, 要使用可变需引入scala.collection.mutable.Map
    var A : Map[Char, Int] = Map();
    var colors = Map("red" -> "#FF0000", "azure" -> "#F0FFFF");
    colors += ("blue" -> "#000000");
    println("colors中的键: " + colors.keys);
    println("colors中的值: " + colors.values);
    
    colors.keys.foreach { key => println("Key = " + key); println("Value = " + colors(key)) };
    
    // 4. 元祖: 不可变, 可以包含不同类型的元素, 实际是一个Tuple, 目前支持的元祖最大长度为22
    var t = (1, 3.14, "Fred");   // 相当于  Tuple3[Int, Float, String]的实例
    var t1 = new Tuple3(1, 3.14, "Fred");
    
    t.productIterator.foreach { i => println("Value = " + i) };
    
    // 5. Option(选项): 代表两种状态, 有值: Some[T], 无值就是None
    val myMap : Map[String, String] = Map("key1" -> "value");
    var value1 : Option[String] = myMap.get("key1");
    var value2 : Option[String] = myMap.get("key2");
    println(value1)  // Some(value)
    println(value2)  // None
    
    
    /****************************类************************************/
    
    var point = new Point(10, 20);
    point.move(5, 6);
    
    var location = new Location(10, 20, 30);
    location.move(5, 6, 6);
    
    var person = new Employee();
    println(person.personString);
    println(person.testOverride);
    
    
    /*************************模式匹配*********************************/
    println(matchTest("two"));  // 打印: 2
    println(matchTest("test")); // 打印: many
    println(matchTest(1));      //打印: one
    println(matchTest(6));      //打印: scala.Int
    /********了解样例类: 用于模式匹配的一种类定义, 用case class定义********/
  }
  
  /**
   * 函数的定义
   * 方式: def functionName([arg1 : arg1Type, arg2 : arg2Type]) : [returnType] = { 
   * 					方法主体; 
   * 					[return returnType] 
   * 		  }
   * 参数列表可选, 不需要参数时不用写
   * [returnType]: 可选, 没有指定时, 根据函数的最后一条执行语句来确定函数的返回类型
   */
  def addInt (a : Int, b : Int) : Int = {
  	a + b;
  }
  
  def addInt (a : Int, b : Int, c : Int) = {
  	a + b + c;
  }
  
  /**
   * Scala解析函数的两种方式:
   * - 传值调用(call-by-value):先计算参数表达式的值,再应用到函数内部
   * - 传名调用(call-by-name): 将未计算的参数表达式直接应用到函数内部
   */
  def time() = {
  	println("获取时间, 单位为纳秒");
  	System.nanoTime();
  }
  
  /**
   * 传名调用函数的定义: 在于形参的定义上的区别 (=>)
   * 注意这里的函数定义: 可以不用等号来定义函数体也是可行的, 另外如果函数体重只有一个语句也可以不用花括号
   */
  def delayed(t : => Long) {
  	println("在delayed方法内");
  	println("参数:" + t);
  	t;
  }
  
  /**
   * 可变参数函数定义
   * 我们可以指明函数的最后一个参数可以重复,通过星号来表示可变
   */
  def printStrings( args : String*) {
  	var i : Int = 0;
  	for (arg <- args) {
  		println("Arg value[" + i + "] = " + arg);
  		i = i + 1;
  	}
  }
  
  /**
   * 默认参数值函数
   */
  def addIntDefault(a : Int=5, b : Int=7) : Int = {
  	return a + b;
  }
  
  /**
   * 高阶函数
   *   操作其他函数的函数, 可以使用其他函数作为参数或者使用函数作为输出结果
   * apply函数中,注意传入函数参数是怎么定义: f : Int(f函数参数类型) => String(f函数的输出类型)
   */
  def apply(f: Int => String, v : Int) = f(v);
  
  // 这里A是泛型的一种定义方式
  def layout[A](x : A) = "[" + x.toString() + "]";
  
  /**
   * 偏应用函数
   *   你不需要提供函数的所有参数,只需要提供部分,或不提供所需参数;函数的定义跟普通函数没有区别, 区别在于使用上
   */
  def log(date : Date, message : String) {
  	println(date + "----" + message);
  }
  
  /**
   * 模式匹配, 类似java中的swith语句, 匹配一个之后自动退出模式匹配
   * 在处理异常时,匹配多个异常时,也要用到这个模式匹配语法
   */
  def matchTest(x : Any) : Any = {
  	x match {
  		case 1 => "one";
  		case "two" => 2;
  		case y : Int => "scala.Int";   // 匹配类型
  		case _ => "many";
  	}
  }
}
```

### 类

```scala

/**
 * Scala中不声明class为public, 默认就是public, 要声明为其他类型时, 可以显示指定private, protected
 */
class Point(xc : Int, yc : Int) {
  var x : Int = xc;  // 默认修饰符为 public
  var y : Int = yc;
  
  def move(dx : Int, dy : Int) {
  	x = x + dx;
  	y = y + dy;
  	
  	println("x的坐标点: " + x);
  	println("y的坐标点: " + y);
  }
}

/**
 * 继承
 */
class Location(xc : Int, yc : Int, zc : Int) extends Point(xc, yc) {
	var z : Int = zc;
	
	def move(dx : Int, dy : Int, dz : Int) {
		x = x + dx;
		y = y + dy;
		z = z + dz;
		
		println("x的坐标点: " + x);
		println("y的坐标点: " + y);
		println("z的坐标点: " + z);
	}
}

abstract class Person {
	var name = "";
	
	/**
	 * 没有方法体的函数, 默认当做抽象方法, 如果不指定返回类型,那么子类实现这个方法返回的将是空类型
	 */
	def personString : String;
	
	def testOverride = "testOverride";
}

class Employee extends Person {
	var salary = 0.0;
	
	/**
	 * 实现父类的抽象方法,不需要override修饰符
	 */
	def personString = {getClass.getName + "[name=" + name + "]"};
	
	/**
	 * 重写父类的方法, 必须要加修饰符 override
	 */
	override def testOverride = "child " + super.testOverride;
}

/**
 * Scala中的接口定义, 里面的方法可以有实现,也可以不实现, 类似java中的抽象类
 * 可以定义属性
 */
trait Equal {
	/**
	 * 没有实现的抽象方法
	 */
	def isEqual(x : Any) : Boolean;
	
	/**
	 * 有实现的方法
	 */
	def isNotEqual(x : Any) : Boolean = !isEqual(x);
}

/**
 * 继承trait
 */
class EqualChild(xc : Int, yc : Int) extends Equal {
	var x : Int = xc;
	var y : Int = yc;
	
	/**
	 * 实现Equal中的抽象方法
	 */
	def isEqual(obj : Any) = {
		obj.isInstanceOf[EqualChild] && obj.asInstanceOf[EqualChild].x == x
	}
}
```