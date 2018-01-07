/*
 * Author : Kahsolt <kahsolt@qq.com>
 * Create Date : 2018-01-06
 * Update Date : 2018-01-07
 * Version : v0.3
 * License : GPLv3
 * Description : 主引擎：数据库配置、生成、连接
 */

package tk.kahsolt.akasha;

import org.apache.log4j.Logger;
import tk.kahsolt.akasha.db.SQLEngine;
import tk.kahsolt.akasha.db.MySQLEngine;
import tk.kahsolt.akasha.db.SQLiteEngine;
import tk.kahsolt.akasha.model.*;

import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

public final class Akasha {

    private static final Logger logger = Logger.getLogger(Akasha.class);

    private String dbUri;
    private SQLEngine dbEngine;
    private ArrayList<Class<? extends Model>> modelList = new ArrayList<>();

    public Akasha() { }
    public Akasha(String dbUri) { this.dbUri = dbUri; }

    public void register(Class<? extends Model> clazz) {
        modelList.add(clazz);
    }
    public void start() {
        dbEngine = dbUri==null ? new SQLiteEngine() :
                dbUri.startsWith("jdbc:mysql") ? new MySQLEngine(dbUri) : new SQLiteEngine(dbUri);
        dbEngine.connect();

        HashMap<Class<? extends Model>, Manager> managers = new HashMap<>();
        HashMap<Class<? extends Model>, ArrayList<? extends Model>> collections = new HashMap<>();
        HashMap<Class<? extends Model>, ArrayList<Field>> fieldsets = new HashMap<>();
        try {
            Field field = Model.class.getDeclaredField("dbEngine");
            field.setAccessible(true); field.set(null, dbEngine);
            field = Model.class.getDeclaredField("managers");
            field.setAccessible(true); field.set(null, managers);
            field = Model.class.getDeclaredField("collections");
            field.setAccessible(true); field.set(null, collections);
            field = Model.class.getDeclaredField("fieldsets");
            field.setAccessible(true); field.set(null, fieldsets);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            e.printStackTrace();
            logger.error("Failed configuring base Model.");
        }
        for (Class<? extends Model> clazz: modelList) {
            try {
                Object mod = clazz.newInstance();
                // 缓存注册字段
                ArrayList<Field> fieldset = new ArrayList<>();
                for(Field field : clazz.getDeclaredFields())
                    if(field.getDeclaredAnnotation(FieldEntry.class)!=null) {
                        field.setAccessible(true);
                        fieldset.add(field);
                    }
                fieldsets.put(clazz, fieldset);
                // 建表/验证存在
                Method method = Model.class.getDeclaredMethod("sqlize");
                method.setAccessible(true);
                method.invoke(mod);
                // 模型化/管理器注册
                method = Model.class.getDeclaredMethod("modelize");
                method.setAccessible(true);
                method.invoke(mod);
            } catch (IllegalAccessException | NoSuchMethodException | InstantiationException | InvocationTargetException e) {
                e.printStackTrace();
                logger.error("Failed wakeup model " + clazz.getSimpleName());
            }
        }
    }
    public void stop() { dbEngine.disconnect(); }

}
