package tk.kahsolt.akasha.example;

import tk.kahsolt.akasha.Akasha;
import tk.kahsolt.akasha.model.Cache;
import tk.kahsolt.akasha.model.FieldEntry;
import tk.kahsolt.akasha.model.Manager;
import tk.kahsolt.akasha.model.Model;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Random;
import java.util.Scanner;
import java.util.UUID;

public class Types extends Model {

    @Manager
    public static Types objects;
    @Cache
    public static ArrayList<Types> cache;

    @FieldEntry
    public Integer integer;
    @FieldEntry
    public Double fractional;
    @FieldEntry
    public UUID uuid;
    @FieldEntry(length = 32)
    public String string;
    @FieldEntry(length = -32)
    public String nstring;
    @FieldEntry(length = 0)
    public String text;
    @FieldEntry
    public Timestamp timestamp;

    public Types() { }
    public Types(Integer integer, Double fractional, UUID uuid, String string, String nstring, String text, Timestamp timestamp) {
        this.integer = integer;
        this.fractional = fractional;
        this.uuid = uuid;
        this.string = string;
        this.nstring = nstring;
        this.text = text;
        this.timestamp = timestamp;
    }

    public static void main(String[] args) {
        Akasha akasha = new Akasha();
        akasha.register(Types.class);
        akasha.start();

        Random random = new Random();

        // 测试所有数据类型
        new Types(
                random.nextInt(),
                random.nextDouble(),
                UUID.randomUUID(),
                "Varsadsjfsjdk",
                "Nstrngas",
                "fshalesuhdajbDSxkhjzsdfhdskjz",
                new Timestamp(random.nextLong())
        ).save();

        // 测试空
        new Types(null, null, null, null, null, null, null).save();

        System.out.println("Count = " + Types.objects.all().size());
        for (Model model : Types.objects.all()) {
            System.out.println(model);
        }

        // 修改
        Types types = (Types)Types.objects.filterNull("integer").getResults().get(0);
        types.integer = 5;
        types.nstring = "sadasd";
        types.save();

        System.out.println("Count = " + Types.objects.all().size());
        for (Model model : Types.objects.all()) {
            System.out.println(model);
        }

        Types.beginUpdate();
        for (Model model : Types.objects.all()) {
            model.remove();
        }
        Types.endUpdate();

        akasha.stop();
    }

}
