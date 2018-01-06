package tk.kahsolt.akasha.example;

import tk.kahsolt.akasha.Akasha;
import tk.kahsolt.akasha.model.Cache;
import tk.kahsolt.akasha.model.FieldEntry;
import tk.kahsolt.akasha.model.Manager;
import tk.kahsolt.akasha.model.Model;

import java.sql.Timestamp;
import java.util.ArrayList;

public class AutoColumn extends Model {

    @Manager
    public static AutoColumn objects;
    @Cache
    public static ArrayList<AutoColumn> cache;

    @FieldEntry
    public Integer first;
    @FieldEntry(defaultValue = "666")
    public Double then;
    @FieldEntry(length = -50)
    public String yetAnother;
    @FieldEntry
    public Timestamp lastone;

    public static void main(String[] args) {
        Akasha akasha = new Akasha();
        akasha.register(AutoColumn.class);
        akasha.start();

        akasha.stop();
    }

}
