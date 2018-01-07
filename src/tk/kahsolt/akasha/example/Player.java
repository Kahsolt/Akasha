package tk.kahsolt.akasha.example;

import tk.kahsolt.akasha.Akasha;
import tk.kahsolt.akasha.model.FieldEntry;
import tk.kahsolt.akasha.model.Manager;
import tk.kahsolt.akasha.model.ManagerEntry;
import tk.kahsolt.akasha.model.Model;

import java.sql.Timestamp;
import java.util.*;

public class Player extends Model {

    @ManagerEntry
    public static Manager objects;

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

    public static HashMap<UUID, Player> players;

    public Player() { } // 供Akasha使用的无参构造
    public Player(String nickname, String password) {
        this.nickname = nickname;
        this.password = password;
    }

    private void addItem(String item, int count) {
        this.info_struct += String.format("%s:%d;", item, count);
    }

    @Override
    public void onModelized() {
        // 建一个Hash表方便按键查找
        players = new HashMap<>();
        for (Model player : Player.objects.all()) {
            players.put(((Player)player).uuid, (Player) player);
        }
    }

    public static void main(String[] args) {
        Akasha akasha;
        if(args.length==1) akasha = new Akasha(args[0]);
        else akasha= new Akasha();
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

        System.out.println(players.get(me.uuid));   // use lookup table

        akasha.stop();
    }

}
