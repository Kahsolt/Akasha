package tk.kahsolt.akasha.model;

import java.sql.Timestamp;
import java.util.HashMap;
import java.util.UUID;

public class TypeMap {

    private static HashMap<Class<?>, String> type_map = new HashMap<Class<?>, String>() {
        {
            // Numeric
            put(Integer.class, "INTEGER");
            put(Double.class, "DOUBLE");
            // Textual
            put(String.class, "VARCHAR");       // 可设置length()从而改变映射为CHAR/TEXT
            put(UUID.class, "CHAR(36)");
            // Temporal
            put(Timestamp.class, "TIMESTAMP");
        }
    };

    public static String lookup(Class<?> clazz) {
        return type_map.get(clazz);
    }
    public static boolean isNumeric(Class<?> clazz) {
        return clazz==Integer.class || clazz==Double.class;
    }
    public static boolean isTextual(Class<?> clazz) { return clazz==String.class || clazz==UUID.class; }
    public static boolean isTemporal(Class<?> clazz) {
        return clazz==Timestamp.class;
    }

}
