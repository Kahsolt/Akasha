/*
 * Author : Kahsolt <kahsolt@qq.com>
 * Create Date : 2017-12-28
 * Update Date : 2018-01-04
 * License : GPLv3
 * Description : 数据表的活动记录，即模型
 */

package tk.kahsolt.akasha.model;

import org.apache.log4j.Logger;
import tk.kahsolt.akasha.db.SQLEngine;
import tk.kahsolt.akasha.db.MySQLEngine;
import tk.kahsolt.sqlbuilder.sql.Query;
import tk.kahsolt.sqlbuilder.sql.Table;

import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Field;
import java.sql.*;
import java.text.DateFormat;
import java.text.ParseException;
import java.util.*;
import java.util.Date;

public abstract class Model {

    private static final Logger logger = Logger.getLogger(Model.class);

    /*
     *  模型基类，自定义模型必须继承自此
     */

    // Kernels & Caches
    private static SQLEngine dbEngine;      // Hypnos注入的数据库引擎
    private static HashMap<Class<? extends Model>, Model> managers;            // 各模型管理器的登记表
    private static HashMap<Class<? extends Model>, ArrayList<Model>> caches;   // 各模型集合列表的登记表
    private static HashMap<String, String> sqlTemplates = new HashMap<>();     // 缓存SQL模板语句

    private ArrayList<Model> modelize() {  // execute sql, pack results to cache
        ArrayList<Model> models = new ArrayList<>();
        try {
            Class<? extends Model> clazz = this.getClass();
            logger.info(String.format("Modelizing from table '%s'.", clazz.getSimpleName()));
            AccessibleObject.setAccessible(clazz.getDeclaredFields(), true);
            AccessibleObject.setAccessible(Model.class.getDeclaredFields(), true);
            String sql = dbEngine.sqlBuilder.select("*").from(clazz.getSimpleName()).end();
            ResultSet rs = dbEngine.query(sql);
            while (rs.next()) {
                Model model = clazz.newInstance();
                try {
                    ArrayList<Field> fields = new ArrayList<>(Arrays.asList(clazz.getDeclaredFields()));
                    fields.addAll(Arrays.asList(clazz.getSuperclass().getDeclaredFields()));
                    for (Field field: fields) {
                        if(field.getDeclaredAnnotation(FieldEntry.class)==null) continue;
                        String name = field.getName();
                        Class<?> type = field.getType();
                        try {
                            Object val = rs.getObject(name, type);
                            field.set(model, val);
                        } catch (SQLException | IllegalAccessException e) {  // for those DB as silly as SQLite
                            // Textual
                            if (type==String.class) field.set(model, rs.getString(name));
                            else if (type==UUID.class) {
                                String obj = rs.getString(name);
                                Object val = obj!=null ? UUID.fromString(obj) : null;
                                field.set(model, val);
                            }
                            // Numeric
                            else if (type==Integer.class) field.set(model, rs.getInt(name));
                            else if (type==Double.class) field.set(model, rs.getDouble(name));
                                // Temporal
                            else if (type==Timestamp.class) field.set(model, rs.getTimestamp(name));
                            else logger.error(String.format("Type '%s' not supported, use String instead!", type));
                        }
                    }
                } catch (IllegalAccessException e) { e.printStackTrace(); }
                models.add(model);
            }
            rs.close();
        } catch (SQLException | IllegalAccessException | InstantiationException e) { e.printStackTrace(); }
        return models;
    }
    private void sqlize() { // reflect model class, generate sql CREATE TABLE and execute it
        Class clazz = this.getClass();
        String clazzName = clazz.getSimpleName();

        if(dbEngine.dbSchema.containsKey(clazzName)) {    // 表修改对照
            HashSet<String> columns = dbEngine.dbSchema.get(clazzName);
            for (Field field : clazz.getDeclaredFields()) {
                FieldEntry fe = field.getDeclaredAnnotation(FieldEntry.class);
                if(fe==null) continue;
                String name = field.getName();
                if(!columns.contains(name)) {
                    logger.info(String.format("Adding new field '%s' to model '%s'.", name, clazzName));
                    Table.Column column = dbEngine.sqlBuilder.alterTable(clazzName)
                            .add(String.format("`%s`", name));
                    buildColumn(field, fe, column);
                    String sql = column.end().end();
                    dbEngine.execute(sql);
                }
            }
        } else {    // 初次建表
            logger.info(String.format("Fresh start, sqlizing for model '%s'.", clazzName));
            AccessibleObject.setAccessible(clazz.getDeclaredFields(), true);
            AccessibleObject.setAccessible(Model.class.getDeclaredFields(), true);
            Table table = dbEngine.sqlBuilder.createTable(String.format("`%s`", clazzName));
            ArrayList<Field> fields = new ArrayList<>();
            fields.addAll(Arrays.asList(Model.class.getDeclaredFields()));
            fields.addAll(Arrays.asList(clazz.getDeclaredFields()));
            for (Field field : fields) {
                FieldEntry fe = field.getDeclaredAnnotation(FieldEntry.class);
                if(fe==null) continue;
                Table.Column column = new Table.Column(String.format("`%s`", field.getName()), table);
                buildColumn(field, fe, column);
                table.column(column);
            }
            ModelEntry me = (ModelEntry) clazz.getDeclaredAnnotation(ModelEntry.class);
            if(me!=null) table.engine(me.engine()).charset(me.charset()).comment(me.comment());
            String sql = table.end();
            if(dbEngine.execute(sql)!=0) logger.warn("CREATE TABLE returned None-Zero value, maybe a fault.");
        }
    }
    private void buildColumn(Field field, FieldEntry fe, Table.Column column) {
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
        column.unique(fe.unique()).notNull(fe.notNull()).initSetCurrent(fe.initSetCurrent()).updateSetCurrent(fe.updateSetCurrent());
    }

    public static void beginUpdate() { dbEngine.begin(); }
    public static void endUpdate() { dbEngine.commit(); }

    // Auto-generated and managed fields
    @FieldEntry(identity = true)
    private Integer id;
    @FieldEntry(initSetCurrent = true)
    public Timestamp create_time;
    @FieldEntry(updateSetCurrent = true)
    public Timestamp update_time;

    // Operations on the cached collection
    private enum CompareOperator {
        NULL, NOT_NULL,
        EQUAL, NOT_EQUAL, LESS, LESS_EQUAL, GREATER, GREATER_EQUAL,
        BETWEEN, LIKE
    }
    public static class Filter {
        private ArrayList<Model> results;  // getResults()之前的查询集合缓冲区
        private Filter(Class<? extends Model> clazz) {
            this.results = new ArrayList<>(caches.get(clazz));
        }

        private boolean compare(Class<?> type, Object lvalue, Object rvalue, CompareOperator operator) {
            if(TypeMap.isNumeric(type)) {
                double rval;
                if(rvalue instanceof Number) {
                    rval = Double.parseDouble(String.valueOf(rvalue));
                } else if(rvalue instanceof String) {
                    try {
                        rval = Double.valueOf((String) rvalue);
                    } catch (NumberFormatException e) {
                        return false;
                    }
                } else return false;
                double lval = Double.parseDouble(String.valueOf(lvalue));
                switch (operator) {
                    case EQUAL:         return lval == rval;
                    case NOT_EQUAL:     return lval != rval;
                    case GREATER:       return lval > rval;
                    case GREATER_EQUAL: return lval >= rval;
                    case LESS:          return lval < rval;
                    case LESS_EQUAL:    return lval <= rval;
                }
            } else if(TypeMap.isTemporal(type)) {
                Date rval;
                if(rvalue instanceof Date) {
                    rval = ((Date) rvalue);
                } else if (rvalue instanceof String) {
                    try {
                        rval = DateFormat.getInstance().parse((String) rvalue);
                    } catch (ParseException e) {
                        return false;
                    }
                } else return false;
                Date lval = ((Date) lvalue);
                switch (operator) {
                    case EQUAL:         return lval.equals(rval);
                    case NOT_EQUAL:     return !lval.equals(rval);
                    case GREATER:       return lval.after(rval);
                    case GREATER_EQUAL: return lval.after(rval) || lval.equals(rval) ;
                    case LESS:          return lval.before(rval);
                    case LESS_EQUAL:    return lval.before(rval) || lval.equals(rval) ;
                }
            } else {    // Textual
                String rval = rvalue.toString();
                String lval = lvalue.toString();
                switch (operator) {
                    case EQUAL:         return lval.equals(rval);
                    case NOT_EQUAL:     return !lval.equals(rval);
                    case GREATER:       return lval.compareTo(rval) > 0;
                    case GREATER_EQUAL: return lval.compareTo(rval) >= 0;
                    case LESS:          return lval.compareTo(rval) < 0;
                    case LESS_EQUAL:    return lval.compareTo(rval) <= 0;
                }
            }
            return false;
        }
        private Filter filterByOperator(String field, CompareOperator operator) {
            ArrayList<Model> pass = new ArrayList<>();
            for (Model model : results) {
                try {
                    Field f = model.getClass().getDeclaredField(field);
                    f.setAccessible(true);
                    Object val = f.get(model);
                    switch (operator) {
                        case NULL:
                            if(val!=null) pass.add(model); break;
                        case NOT_NULL:
                            if(val==null) pass.add(model); break;
                    }
                } catch (NoSuchFieldException | IllegalAccessException e) {
                    pass.add(model);
                }
            }
            results.removeAll(pass);
            return this;
        }
        private Filter filterByOperator(String field, CompareOperator operator, Object value) {
            ArrayList<Model> pass = new ArrayList<>();
            for (Model model : results) {
                try {
                    Field f = model.getClass().getDeclaredField(field);
                    f.setAccessible(true);
                    Class<?> type = f.getType();
                    Object val = f.get(model);
                    switch (operator) {
                        case EQUAL:
                        case NOT_EQUAL:
                        case GREATER:
                        case GREATER_EQUAL:
                        case LESS:
                        case LESS_EQUAL:
                            if(!compare(type, val, value, operator))
                                pass.add(model);
                            break;
                        case LIKE:  // only valid for String/UUID
                            if(!(TypeMap.isTextual(type) && (val.toString().contains(value.toString()))))
                                pass.add(model);
                            break;
                    }
                } catch (NoSuchFieldException | IllegalAccessException e) {
                    pass.add(model);
                }
            }
            results.removeAll(pass);
            return this;
        }
        private Filter filterByOperator(String field, CompareOperator operator, Object minValue, Object maxValue) {
            ArrayList<Model> pass = new ArrayList<>();
            for (Model model : results) {
                try {
                    Field f = model.getClass().getDeclaredField(field);
                    f.setAccessible(true);
                    Class<?> type = f.getType();
                    Object val = f.get(model);
                    switch (operator) {
                        case BETWEEN:
                            if(compare(type, val, minValue, CompareOperator.LESS)) { pass.add(model); break; }
                            if(compare(type, val, maxValue, CompareOperator.GREATER)) { pass.add(model); break; }
                    }
                } catch (NoSuchFieldException | IllegalAccessException e) {
                    pass.add(model);
                }
            }
            results.removeAll(pass);
            return this;
        }
        public Filter filterNull(String field) { return filterByOperator(field, CompareOperator.NULL); }
        public Filter filterNotNull(String field) { return filterByOperator(field, CompareOperator.NOT_NULL); }
        public Filter filterEqual(String field, Object value) { return filterByOperator(field, CompareOperator.EQUAL, value); }
        public Filter filterNotEqual(String field, Object value) { return filterByOperator(field, CompareOperator.NOT_EQUAL, value); }
        public Filter filterGreater(String field, Object value) { return filterByOperator(field, CompareOperator.GREATER, value); }
        public Filter filterGreaterEqual(String field, Object value) { return filterByOperator(field, CompareOperator.GREATER_EQUAL, value); }
        public Filter filterLess(String field, Object value) { return filterByOperator(field, CompareOperator.LESS, value); }
        public Filter filterLessEqual(String field, Object value) { return filterByOperator(field, CompareOperator.LESS_EQUAL, value); }
        public Filter filterLike(String field, Object value) { return filterByOperator(field, CompareOperator.LIKE, value);}
        public Filter filterBetween(String field, Object minValue, Object maxValue) { return filterByOperator(field, CompareOperator.BETWEEN, minValue, maxValue);}

        public ArrayList<Model> getResults() { return results; }
    }
    public Filter filterNull(String field) { return new Filter(this.getClass()).filterNull(field); }
    public Filter filterNotNull(String field) { return new Filter(this.getClass()).filterNotNull(field); }
    public Filter filterEqual(String field, Object value) { return new Filter(this.getClass()).filterEqual(field, value); }
    public Filter filterNotEqual(String field, Object value) { return new Filter(this.getClass()).filterNotEqual(field, value); }
    public Filter filterGreater(String field, Object value) { return new Filter(this.getClass()).filterGreater(field , value); }
    public Filter filterGreaterEqual(String field, Object value) { return new Filter(this.getClass()).filterGreaterEqual(field, value); }
    public Filter filterLess(String field, Object value) { return new Filter(this.getClass()).filterLess(field, value); }
    public Filter filterLessEqual(String field, Object value) { return new Filter(this.getClass()).filterLessEqual(field, value); }
    public Filter filterBetween(String field, Object minValue, Object maxValue) { return new Filter(this.getClass()).filterBetween(field, minValue, maxValue); }
    public Filter filterLike(String field, Object value) { return new Filter(this.getClass()).filterLike(field, value);}
    public ArrayList<Model> all() { return new ArrayList<>(caches.get(this.getClass())); }
    public Model get(String field, Object value) {  // shortcut
        ArrayList<Model> res = filterEqual(field, value).getResults();
        return res.size()==1 ? res.get(0) : null;
    }
    public void saveAll() {  // shortcut
        dbEngine.begin();
        for (Model model : caches.get(this.getClass())) {
            try {
                model.save();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        dbEngine.commit();
    }

    // Operations on one model instance
    public boolean exists() { return id!=null; }
    public boolean remove() { return id!=null && delete(); }
    public boolean save() { return id==null ? insert() : update(); }
    private boolean insert() {
        if(isManager()) return false;

        Class<? extends Model> clazz = this.getClass();
        String clazzName = clazz.getSimpleName();
        Field[] fields = clazz.getDeclaredFields();
        AccessibleObject.setAccessible(fields, true);

        String sqlName = "insert_%s" + clazzName;
        String sql = sqlTemplates.get(sqlName);
        if (sql == null) {
            ArrayList<String> columns = new ArrayList<>();
            for (Field field : fields) {
                if(field.getDeclaredAnnotation(FieldEntry.class)!=null)
                    columns.add(field.getName());
            }
            String[] cols = columns.toArray(new String[columns.size()]);
            sql = dbEngine.sqlBuilder.insert(clazzName).into(cols).values().end();
            sqlTemplates.put(sqlName, sql);
        }

        ArrayList<Object> values = new ArrayList<>();
        for (Field field : fields) {
            if(field.getDeclaredAnnotation(FieldEntry.class)==null) continue;
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

        caches.get(this.getClass()).add(this);  // add to cache
        return true;
    }
    private boolean update() {
        if(isManager()) return false;

        Class<? extends Model> clazz = this.getClass();
        String clazzName = clazz.getSimpleName();
        Field[] fields = clazz.getDeclaredFields();
        AccessibleObject.setAccessible(fields, true);

        String sqlName = "update_%s" + clazzName;
        String sql = sqlTemplates.get(sqlName);
        if (sql == null) {
            Query sqlUpdate = dbEngine.sqlBuilder.update(clazz.getSimpleName());
            for (Field field : fields) {
                if(field.getDeclaredAnnotation(FieldEntry.class)!=null)
                    sqlUpdate.set(field.getName());
            }
            sql = sqlUpdate.where("id").eq().end();
            sqlTemplates.put(sqlName, sql);
        }

        ArrayList<Object> values = new ArrayList<>();
        for (Field field : fields) {
            if (field.getDeclaredAnnotation(FieldEntry.class) == null) continue;
            try {
                Object val = field.get(this);
                if(val instanceof UUID) val = val.toString();   // FIXME: 上传数据类型转换，特殊处理是不好的设计
                values.add(val);
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            }
        }
        if(values.size()==0) return false;    // infers no changes at all

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
        if(isManager()) return false;

        String sql = String.format("DELETE FROM %s WHERE id = ?;", this.getClass().getSimpleName());
        if(dbEngine.execute(sql, id)!=1) {
            logger.error("DELETE rejected, probably not exists or breaks constraints.");
            return false;
        }

        id = null;
        caches.get(this.getClass()).remove(this);  // remove from cache
        return true;
    }
    private boolean isManager() { return managers.get(this.getClass())==this; }

    // Misc
    public Integer getId() { return id; }
    @Override
    public String toString() {
        Class<? extends Model> clazz = this.getClass();
        String clazzName = clazz.getSimpleName();
        ArrayList<String> segs = new ArrayList<>();
        for(Field field : this.getClass().getDeclaredFields()) {
            if(field.getDeclaredAnnotation(FieldEntry.class)==null) continue;
            field.setAccessible(true);
            String name = field.getName();
            String value = "<NoAccess>";
            try {
                Object val = field.get(this);
                value = val==null ? "" : val.toString();
            } catch (IllegalAccessException e) { }
            segs.add(String.format("%s=%s", name, value));
        }
        String fields = String.join(", ", segs);
        return String.format("%s{id=%d, %s, create_time=%s, update_time=%s}",
                clazzName, id, fields, create_time, update_time);
    }

}
