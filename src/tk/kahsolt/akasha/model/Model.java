package tk.kahsolt.akasha.model;

import org.apache.log4j.Logger;
import tk.kahsolt.akasha.db.SQLEngine;
import tk.kahsolt.akasha.db.MySQLEngine;
import tk.kahsolt.sqlbuilder.sql.Query;
import tk.kahsolt.sqlbuilder.sql.Table;

import java.lang.reflect.Field;
import java.sql.*;
import java.util.*;

public abstract class Model {

    private static final Logger logger = Logger.getLogger(Model.class);

    /*
     *  模型基类，自定义模型必须继承自此
     */

    // Kernels & Caches
    private static SQLEngine dbEngine;                                              // 数据库引擎
    private static HashMap<Class<? extends Model>, Manager> managers;               // 登记各模型管理器
    private static HashMap<Class<? extends Model>, HashSet<Model>> collections;     // 登记各模型集合
    private static HashMap<Class<? extends Model>, ArrayList<Field>> fieldsets;     // 缓存各模型自定义字段集(不含默认字段)
    private static HashMap<String, String> sqlTemplates = new HashMap<>();          // 缓存SQL模板语句
    private void modelize() {  // execute sql, pack results to cache
        Class<? extends Model> clazz = this.getClass();
        String clazzName = clazz.getSimpleName();
        HashSet<Model> collection = new HashSet<>();
        logger.info(String.format("Modelizing from table '%s'.", clazzName));
        try {
            String sql = dbEngine.sqlBuilder.select("*").from(clazzName).end();
            ResultSet rs = dbEngine.query(sql);
            ArrayList<Field> fields = new ArrayList<>(fieldsets.get(clazz));
            try {
                fields.add(Model.class.getDeclaredField("id"));
                fields.add(Model.class.getDeclaredField("create_time"));
                fields.add(Model.class.getDeclaredField("update_time"));
            } catch (NoSuchFieldException e) { /* INTERNAL ERROR */ }
            while (rs.next()) {
                Model model = clazz.newInstance();
                try {
                    for (Field field: fields) {
                        String name = field.getName();
                        Class<?> type = field.getType();
                        try {
                            field.set(model, rs.getObject(name, type));
                        } catch (SQLException | IllegalAccessException e) {  // for those DB as silly as SQLite
                            if (type==String.class) field.set(model, rs.getString(name));
                            else if (type==Integer.class) field.set(model, rs.getInt(name));
                            else if (type==Double.class) field.set(model, rs.getDouble(name));
                            else if (type==Timestamp.class) field.set(model, rs.getTimestamp(name));
                            else if (type==UUID.class) {
                                String obj = rs.getString(name);
                                field.set(model, obj!=null ? UUID.fromString(obj) : null);
                            } else logger.error(String.format("Type '%s' not supported, see TypeMap!", type));
                        }
                    }
                } catch (IllegalAccessException e) { e.printStackTrace(); }
                collection.add(model);
            }
            rs.close();
        } catch (SQLException | IllegalAccessException | InstantiationException e) { e.printStackTrace(); }
        collections.put(clazz, collection);

        Manager manager = new Manager(clazz, collection);
        managers.put(clazz, manager);
        try {
            for(Field field : clazz.getDeclaredFields()) {
                if(field.getDeclaredAnnotation(ManagerEntry.class)!=null) {
                    field.setAccessible(true);
                    field.set(null, manager);
                    break;
                }
            }
        } catch (IllegalAccessException e) { e.printStackTrace(); }
        onModelized();
    }
    private void sqlize() { // reflect model class, generate sql CREATE TABLE and execute it
        Class<? extends Model> clazz = this.getClass();
        String clazzName = clazz.getSimpleName();
        ArrayList<Field> fields = fieldsets.get(clazz);

        if(dbEngine.dbSchema.containsKey(clazzName)) {    // 表修改对照
            HashSet<String> columns = dbEngine.dbSchema.get(clazzName);
            for (Field field : fields) {
                String name = field.getName();
                if(!columns.contains(name)) {
                    logger.info(String.format("Changes detected, add field '%s' to model '%s'.", name, clazzName));
                    Table table = dbEngine.sqlBuilder.alterTable(clazzName);
                    table.add(String.format("`%s`", name));
                    String sql = defineColumn(field, table).end().end();
                    dbEngine.execute(sql);
                }
            }
        } else {    // 初次建表
            logger.info(String.format("Fresh start, sqlizing for model '%s'.", clazzName));
            Table table = dbEngine.sqlBuilder.createTable(String.format("`%s`", clazzName));
            try {
                table.column(defineColumn(Model.class.getDeclaredField("id"), table));
                for (Field field : fields) table.column(defineColumn(field, table));
                table.column(defineColumn(Model.class.getDeclaredField("create_time"), table));
                table.column(defineColumn(Model.class.getDeclaredField("update_time"), table));
            } catch (NoSuchFieldException e) { /* INTERNAL ERROR */ }
            ModelEntry me = clazz.getDeclaredAnnotation(ModelEntry.class);
            if(me!=null) table.engine(me.engine()).charset(me.charset()).comment(me.comment());
            String sql = table.end();
            if(dbEngine.execute(sql)!=0) logger.warn("CREATE TABLE returned None-Zero value, maybe a fault.");
        }
    }
    private Table.Column defineColumn(Field field, Table table) {
        FieldEntry fe = field.getDeclaredAnnotation(FieldEntry.class);
        Table.Column column = new Table.Column(String.format("`%s`", field.getName()), table);
        // Type + length
        String t = TypeMap.lookup(field.getType());
        if(t.equals("VARCHAR")) {
            if(fe.length()>0) column.type(String.format("VARCHAR(%d)", fe.length()));
            else if(fe.length()==0) column.type("TEXT");
            else column.type(String.format("CHAR(%d)", -fe.length()));
        } else column.type(t);
        // Default
        if(!fe.defaultValue().isEmpty()) {
            String strVal = fe.defaultValue();
            Object val;
            try {
                val = Integer.parseInt(strVal);
            } catch (NumberFormatException e1) {
                try {
                    val = Double.parseDouble(strVal);
                } catch (NumberFormatException e2) {
                    val = strVal;
                }
            }
            column.defaultValue(val);
        }
        // PK + AI
        if(fe.identity()) column.type("INTEGER").autoIncrement();
        // Attributes
        column.unique(fe.unique()).notNull(fe.notNull())
                .initSetCurrent(fe.initSetCurrent())
                .updateSetCurrent(fe.updateSetCurrent());
        return column;
    }

    // Event Hook
    protected void onModelized() { /* called after modelize() */ }

    // Auto-generated and managed fields
    @FieldEntry(identity = true)
    private Integer id;
    @FieldEntry(initSetCurrent = true)
    public Timestamp create_time;
    @FieldEntry(updateSetCurrent = true)
    public Timestamp update_time;

    // Operations on one model instance
    public static void beginUpdate() { dbEngine.begin(); }
    public static void endUpdate() { dbEngine.commit(); }
    public boolean exists() { return id!=null; }
    public boolean remove() { return id!=null && delete(); }
    public boolean save() { return id==null ? insert() : update(); }
    private boolean insert() {
        Class<? extends Model> clazz = this.getClass();
        String clazzName = clazz.getSimpleName();
        ArrayList<Field> fields = fieldsets.get(clazz);

        String sqlName = "insert_%s" + clazzName;
        String sql = sqlTemplates.get(sqlName);
        if (sql == null) {
            ArrayList<String> columns = new ArrayList<>();
            for (Field field : fields) columns.add(field.getName());
            String[] cols = columns.toArray(new String[columns.size()]);
            sql = dbEngine.sqlBuilder.insert(clazzName).into(cols).values().end();
            sqlTemplates.put(sqlName, sql);
            logger.info("INSERT template cached");
        }
        ArrayList<Object> values = new ArrayList<>();
        for (Field field : fields) {
            try {
                Object val = field.get(this);
                if(val instanceof UUID) val = val.toString();   // FIXME: 上传数据类型转换，特殊处理是不好的设计
                values.add(val);
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            }
        }
        Object[] vals = values.toArray(new Object[values.size()]);
        if (dbEngine.execute(sql, vals) != 1) {
            logger.error("INSERT rejected, probably breaks constraints.");
            return false;
        }

        sqlName = "insert_meta_%s" + clazzName;
        sql = sqlTemplates.get(sqlName);
        if(sql==null) {
            if(dbEngine instanceof MySQLEngine)
                sql = dbEngine.sqlBuilder.select("id", "create_time", "update_time").from(clazzName)
                        .where("id").eq("SELECT @@IDENTITY").end();
            else
                sql = dbEngine.sqlBuilder.select("id", "create_time", "update_time").from(clazzName)
                        .where("id").eq("SELECT MAX(id) FROM " + clazzName).end();
            sqlTemplates.put(sqlName, sql);
        }
        ArrayList<Object> res = dbEngine.fetch(sql);
        id = ((Number) res.get(0)).intValue();
        create_time = res.get(1) instanceof Timestamp ? (Timestamp) res.get(1) : Timestamp.valueOf(res.get(1).toString());
        update_time = res.get(2) instanceof Timestamp ? (Timestamp) res.get(2) : Timestamp.valueOf(res.get(2).toString());

        collections.get(this.getClass()).add(this);  // add to cache
        return true;
    }
    private boolean update() {
        Class<? extends Model> clazz = this.getClass();
        String clazzName = clazz.getSimpleName();
        ArrayList<Field> fields = fieldsets.get(clazz);

        String sqlName = "update_%s" + clazzName;
        String sql = sqlTemplates.get(sqlName);
        if (sql == null) {
            Query sqlUpdate = dbEngine.sqlBuilder.update(clazz.getSimpleName());
            for (Field field : fields) sqlUpdate.set(field.getName());
            sql = sqlUpdate.where("id").eq().end();
            sqlTemplates.put(sqlName, sql);
            logger.info("UPDATE template cached");
        }
        ArrayList<Object> values = new ArrayList<>();
        for (Field field : fields) {
            try {
                Object val = field.get(this);
                if(val instanceof UUID) val = val.toString();   // FIXME: 上传数据类型转换，特殊处理是不好的设计
                values.add(val);
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            }
        }

        values.add(id);
        Object[] vals = values.toArray(new Object[values.size()]);
        if (dbEngine.execute(sql, vals) != 1) {
            logger.error("UPDATE rejected, probably not exists or breaks constraints.");
            return false;
        }

        sql = String.format("SELECT update_time FROM %s WHERE id = ?;", clazzName);
        Object res = dbEngine.acquire(sql, id);
        update_time = res instanceof Timestamp ? (Timestamp) res : Timestamp.valueOf(res.toString());
        return true;
    }
    private boolean delete() {
        String sql = String.format("DELETE FROM %s WHERE id = ?;", this.getClass().getSimpleName());
        if(dbEngine.execute(sql, id)!=1) {
            logger.error("DELETE rejected, probably not exists or breaks constraints.");
            return false;
        }
        id = null;
        collections.get(this.getClass()).remove(this);  // remove from cache
        return true;
    }

    // Misc
    public Manager objects() { return managers.get(this.getClass()); }
    public Integer id() { return id; }
    @Override
    public String toString() {
        Class<? extends Model> clazz = this.getClass();
        String clazzName = clazz.getSimpleName();
        ArrayList<String> segs = new ArrayList<>();
        for(Field field : fieldsets.get(clazz)) {
            field.setAccessible(true);
            String name = field.getName();
            String value = "<NoAccess>";
            try {
                Object val = field.get(this);
                value = val==null ? "" : TypeMap.isNumeric(val.getClass()) ?
                        val.toString() : String.format("'%s'", val.toString());
            } catch (IllegalAccessException ignored) { }
            segs.add(String.format("%s=%s", name, value));
        }
        String fields = String.join(", ", segs);
        return String.format("%s{id=%s, %s, create_time='%s', update_time='%s'}",
                clazzName, id, fields, create_time, update_time);
    }

}
