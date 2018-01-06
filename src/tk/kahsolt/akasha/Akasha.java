/*
 * Author : Kahsolt <kahsolt@qq.com>
 * Create Date : 2018-01-06
 * Update Date : 2018-01-06
 * Version : v0.1
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
import java.util.HashMap;

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

        HashMap<Class<? extends Model>, Object> managers = new HashMap<>();
        HashMap<Class<? extends Model>, ArrayList<?>> caches = new HashMap<>();
        try {
            Class<?> supclazz = Model.class;
            AccessibleObject.setAccessible(supclazz.getDeclaredFields(), true);
            Field field = supclazz.getDeclaredField("dbEngine");
            field.setAccessible(true);
            field.set(null, dbEngine);
            field = supclazz.getDeclaredField("managers");
            field.setAccessible(true);
            field.set(null, managers);
            field = supclazz.getDeclaredField("caches");
            field.setAccessible(true);
            field.set(null, caches);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            e.printStackTrace();
            logger.error("Failed configuring base Model.");
        }
        for (Class<? extends Model> clazz: modelList) {
            Class<?> supclazz = clazz.getSuperclass();
            boolean foundManager = false;
            boolean foundCache = false;
            try {
                Object mod = clazz.newInstance();
                // 建表/验证存在
                Method method = supclazz.getDeclaredMethod("sqlize");
                method.setAccessible(true);
                method.invoke(mod);

                // 注册Manager/Cache
                for(Field field : clazz.getDeclaredFields()) {
                    if(!foundManager && field.getDeclaredAnnotation(Manager.class)!=null) {
                        field.setAccessible(true);
                        field.set(null, mod);
                        managers.put(clazz, mod);
                        foundManager = true;
                    }
                    if(!foundCache && field.getDeclaredAnnotation(Cache.class)!=null) {
                        foundCache = true;
                        method = supclazz.getDeclaredMethod("modelize");
                        method.setAccessible(true);
                        @SuppressWarnings("unchecked")
                        ArrayList<Model> cache = (ArrayList) method.invoke(mod);
                        caches.put(clazz, cache);
                    }
                    if(foundManager && foundCache) break;
                }
            } catch (NoSuchMethodException | IllegalAccessException | InstantiationException | InvocationTargetException e) {
                e.printStackTrace();
                logger.error("Failed wakeup model " + clazz.getSimpleName());
            } finally {
                if(!foundManager) logger.warn(String.format("No manager found for model '%s', not recommended.", clazz.getSimpleName()));
                if(!foundCache) logger.error(String.format("No cache found for model '%s'.", clazz.getSimpleName()));
            }
        }
    }
    public void stop() { dbEngine.disconnect(); }

}
