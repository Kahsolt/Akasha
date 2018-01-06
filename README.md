# Akasha
---

    Hyperlight-weight pseudo-ORM framework for SQLite3
    Aimed to handle simple player data in Minecraft plugins

## General
  Akasha is a simplified and targeted version of [Hypnos](https://github.com/Kahsolt/Hypnos)
  - Differences from Hypnos
    - Support less data types, but faster when modelizing
    - No foreign key support
    - Only work in cached mode, so you can delay DB write
  - Dependencies
      - [SQLBuilder](https://github.com/Kahsolt/SQLBuilder)
      - jdbc driver(sqlite/mysql)
      - log4j
  - Examples
    - Code: tk/kahsolt/akasha/example/Player.java
    - Output: run `java -jar Akasha.jar`
  - IDE Builds
    - Intellij artifact - JAR

## Changelog

  - v0.2
```java
/*
 * 1.支持启动时自动检测模型类字段的更改，自动向数据表增加新列。
 *   考虑安全性及向Sqlite兼容，不提供自动删除列和修改列数据类型的操作
 *   例如example.AutoColumn，每次增加一个字段后重启程序
 *   启动时Model.sqlize()会感应到字段增加，自动ALTER TABLE
 * 2.example.Pressure中进行了一些性能测试，看起来还不算太糟糕
 *   (毕竟没有LINQ操作集合就是很麻烦)
 */
@FieldEntry
public Integer first;
@FieldEntry(defaultValue = "666")
public Double then;
@FieldEntry(length = -50)
public String yetAnother;
@FieldEntry
public Timestamp lastone;
```
日志中可见如下记录：

    2018-01-07 00:20:19 <main:0> [INFO] Fresh start, sqlizing for model 'AutoColumn'.
    2018-01-07 00:20:20 <main:300> [INFO] Modelizing from table 'AutoColumn'.
    2018-01-07 00:20:46 <main:0> [INFO] Adding new field 'then' to model 'AutoColumn'.
    2018-01-07 00:20:46 <main:309> [INFO] Modelizing from table 'AutoColumn'.
    2018-01-07 00:20:53 <main:0> [INFO] Adding new field 'yetAnother' to model 'AutoColumn'.
    2018-01-07 00:20:53 <main:330> [INFO] Modelizing from table 'AutoColumn'.
    2018-01-07 00:21:00 <main:0> [INFO] Adding new field 'lastone' to model 'AutoColumn'.
    2018-01-07 00:21:00 <main:316> [INFO] Modelizing from table 'AutoColumn'.

  - v0.1
    基本功能OK，参考下文Exmaple

## Exmaple
```java
/*
* 写法基本与Hypnos模型相同，但所有的filter方法都是操作本地的cache集合
*/
public class Player extends Model {

    @Manager
    public static Player objects;
    @Cache
    public static ArrayList<Player> cache;

    @FieldEntry(unique = true)
    public UUID uuid;
    @FieldEntry(length = 32)
    public String nickname;
    @FieldEntry(length = 64)
    public String password;
    @FieldEntry
    public Integer level;
    @FieldEntry
    public Double money;
    @FieldEntry
    public Timestamp online_time;  // stands for a time period in seconds (getTime() makes sense)
    @FieldEntry
    public Timestamp birthday;     // stands for real datetime (toString() makes sense)
    @FieldEntry(length = 0)
    public String info_struct;     // a structured info string, parse it by yourself!

    public Player() { }
    public Player(String nickname, String password) { this.nickname = nickname; this.password = password; }

    private void addItem(String item, int count) {
        this.info_struct += String.format("%s:%d;", item, count);
    }

    public static void main(String[] args) {
        Akasha akasha = new Akasha();
        akasha.register(Player.class);
        akasha.start();

        new Player("kahsolt", "meow-pass").save();
        new Player("luper", "A-ha?").save();

        // 更多filter请参考example.Pressure
        ArrayList<Model> res = Player.objects.filterLike("password", "-").getResults();
        for (Model model : res) {
            System.out.println(model);
        }

        Player me = (Player)Player.objects.get("nickname", "kahsolt");
        if(me!=null) System.out.println("我找到你啦！");
        // 处理简单标量
        me.uuid = UUID.randomUUID();
        me.level = 666;
        me.money = 2.3333;
        me.online_time = new Timestamp(123456789L);
        // 处理日期
        Calendar cal = Calendar.getInstance();
        cal.set(2007, Calendar.APRIL,1);
        me.birthday = new Timestamp(cal.getTime().getTime());
        // 处理结构化字符串
        me.info_struct = "bread:5;stone:20;";
        me.addItem("coal", 10); // me.removeItem("gold", 5); 自己解析字符串我懒得写了:)
        me.save();
        System.out.println(me);

        me.remove();

        akasha.stop();
    }
}
```