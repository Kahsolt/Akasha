package tk.kahsolt.akasha.model;

import java.lang.annotation.*;

@Documented
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface FieldEntry {

    /*
     *  用来注册类字段到数据库列的映射，字段请以public修饰
     */

    /*
     *  类字段类型为String时，所对应数据库列的最大长度，默认VARCHAR(128)
     *    1.正数表示使用VARCHAR(length)
     *    2.负数表示使用CHAR(-length)
     *    3.0表示使用TEXT
     */
    int length() default 128;

    /*
     *  设置为惟一键UNIQUE；通常数据库会自动建立非聚簇索引
     */
    boolean unique() default false;

    /*
     *  设置为非空(NOT NULL)
     *    注意：若非空设置为false，且字段无默认值则会生成可空列
     */
    boolean notNull() default false;

    /*
     *  设置默认值，若是数值则会自动转换类型
     */
    String defaultValue() default "";

    /*
     *  设置列注释
     */
    String comment() default "";


    //  以下参数对于自定义model不一定安全，不建议使用
    /*
     *  设置为自增主键PK+AI(默认字段id使用)
     */
    boolean identity() default false;
    /*
     *  设置时间戳默认值为当前时间(DEFAULT CURRENT_TIMESTAMP)
     *  设置更新时时间戳为当前时间(ON UPDATE CURRENT_TIMESTAMP)
     *    注意：该类字段类型必须兼容数据库的TIMESTAMP类型
     */
    boolean initSetCurrent() default false;
    boolean updateSetCurrent() default false;
}

