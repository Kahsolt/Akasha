package tk.kahsolt.akasha.example;

import tk.kahsolt.akasha.Akasha;
import tk.kahsolt.akasha.model.FieldEntry;
import tk.kahsolt.akasha.model.Manager;
import tk.kahsolt.akasha.model.ManagerEntry;
import tk.kahsolt.akasha.model.Model;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Random;
import java.util.UUID;

public class Pressure extends Model {

    @ManagerEntry
    public static Manager objects;

    @FieldEntry
    public UUID uuid;
    @FieldEntry
    public Integer int1;
    @FieldEntry
    public Integer int2;
    @FieldEntry
    public Integer int3;
    @FieldEntry
    public Integer int4;
    @FieldEntry
    public Integer int5;
    @FieldEntry
    public Integer int6;
    @FieldEntry
    public Double flt1;
    @FieldEntry
    public Double flt2;
    @FieldEntry
    public Double flt3;
    @FieldEntry
    public Double flt4;
    @FieldEntry(length = 32)
    public String str1;
    @FieldEntry(length = 64)
    public String str2;
    @FieldEntry(length = 128)
    public String str3;
    @FieldEntry(length = 200)
    public String str4;
    @FieldEntry(length = -32)
    public String nstr1;
    @FieldEntry(length = -128)
    public String nstr2;
    @FieldEntry(length = 0)
    public String txt1;
    @FieldEntry(length = 0)
    public String txt2;
    @FieldEntry(length = 0)
    public String txt3;
    @FieldEntry
    public Timestamp ts1;
    @FieldEntry
    public Timestamp ts2;
    @FieldEntry
    public Timestamp ts3;

    public static void main(String[] args) {
        long time;
        Random random = new Random();
        ArrayList<Model> models;

        Akasha akasha = new Akasha();
        akasha.register(Pressure.class);

        // 构表：0.7s
        // 模型化：1.2s
        time = System.currentTimeMillis();
        akasha.start();
        System.out.println(String.format("start(): %d", System.currentTimeMillis()-time));

        // 插入数据：2.5s
        boolean doInsert = false;
        if(doInsert) {
            time = System.currentTimeMillis();
            Pressure.beginUpdate();
            for (int i = 0; i < 5000; i++) {
                Pressure p = new Pressure();
                p.uuid = UUID.randomUUID();
                p.int1 = random.nextInt(); p.int2 = random.nextInt();
                p.int3 = random.nextInt(); p.int4 = random.nextInt();
                p.int5 = random.nextInt(); p.int6 = random.nextInt();
                p.flt1 = random.nextDouble(); p.flt2 = random.nextDouble();
                p.flt3 = random.nextDouble(); p.flt4 = random.nextDouble();
                p.str1 = UUID.randomUUID().toString().substring(0, 30);
                p.str2 = UUID.randomUUID().toString();
                p.str3 = UUID.randomUUID().toString() + UUID.randomUUID().toString();
                p.str4 = UUID.randomUUID().toString() + UUID.randomUUID().toString() + UUID.randomUUID().toString();
                p.nstr1 = UUID.randomUUID().toString() + UUID.randomUUID().toString();
                p.nstr2 = UUID.randomUUID().toString();
                p.txt1 = UUID.randomUUID().toString() + UUID.randomUUID().toString() + UUID.randomUUID().toString();
                p.txt2 = UUID.randomUUID().toString() + UUID.randomUUID().toString() + UUID.randomUUID().toString() + UUID.randomUUID().toString() + UUID.randomUUID().toString()+ UUID.randomUUID().toString();
                p.ts1 = new Timestamp(random.nextLong());
                p.ts2 = new Timestamp(random.nextLong());
                p.ts3 = new Timestamp(random.nextLong());
                p.save();
            }
            Pressure.endUpdate();
            System.out.println(String.format("Bulk INSERT: %d", System.currentTimeMillis()-time));
            System.out.println("Count = " + Pressure.objects.all().size());
        }

        // 简单筛选：48s
        time = System.currentTimeMillis();
        for (int i = 0; i < 1000; i++) {
            models = Pressure.objects.filterNull("ts1").getResults();
            models = Pressure.objects.filterNotNull("int1").getResults();
            models = Pressure.objects.filterEqual("int2", random.nextInt()).getResults();
            models = Pressure.objects.filterNotEqual("flt2", random.nextDouble()).getResults();
            models = Pressure.objects.filterGreater("ts2", new Timestamp(random.nextInt())).getResults();
            models = Pressure.objects.filterGreaterEqual("flt1", random.nextDouble()).getResults();
            models = Pressure.objects.filterLess("nstr2", "ABC").getResults();
            models = Pressure.objects.filterLessEqual("int4", random.nextInt()).getResults();
            models = Pressure.objects.filterLike("txt1", "5CF").getResults();
            models = Pressure.objects.filterLike("nstr1", "AB").getResults();
            models = Pressure.objects.filterBetween("int3", random.nextInt(5), random.nextInt()).getResults();
        }
        System.out.println(String.format("simple filter: %d", System.currentTimeMillis()-time));

        // 级联筛选：23s
        time = System.currentTimeMillis();
        for (int i = 0; i < 1000; i++) {
            models = Pressure.objects.filterNotNull("ts1")
                    .filterGreaterEqual("int3", random.nextInt())
                    .filterLike("txt2", "AB")
                    .filterBetween("flt1", random.nextDouble(), 2 * random.nextDouble())
                    .getResults();
            if(models.size()>=1) ((Pressure)models.get(0)).str3 = UUID.randomUUID().toString();
            models = Pressure.objects.filterLessEqual("txt2", "CD")
                    .filterGreaterEqual("int4", random.nextInt())
                    .filterLike("nstr2", "AB")
                    .filterBetween("ts2", new Timestamp(random.nextInt()), new Timestamp(random.nextLong()))
                    .getResults();
            if(models.size()>=1) ((Pressure)models.get(0)).ts2 = new Timestamp(random.nextLong());
            models = Pressure.objects.filterNotNull("txt2")
                    .filterGreaterEqual("int6", random.nextInt())
                    .filterBetween("nstr2", "95", "CD")
                    .filterLike("txt2", "AB")
                    .getResults();
            if(models.size()>=1) ((Pressure)models.get(0)).flt2 = random.nextDouble();
        }
        System.out.println(String.format("filter & modify: %d", System.currentTimeMillis()-time));

        // 更新：3s
        time = System.currentTimeMillis();
        Pressure.beginUpdate();
        for (Model model : Pressure.objects.all()) {
            model.save();
        }
        Pressure.endUpdate();
        System.out.println(String.format("UPDATE: %d", System.currentTimeMillis()-time));

        // 删除：1316/1161=12s
        boolean doDelete = false;
        if(doDelete) {
            time = System.currentTimeMillis();
            Pressure.beginUpdate();
            for (Model model : Pressure.objects.all()) {
                model.remove();
            }
            Pressure.endUpdate();
            System.out.println(String.format("DELETE: %d", System.currentTimeMillis()-time));
        }

        akasha.stop();
    }

}
