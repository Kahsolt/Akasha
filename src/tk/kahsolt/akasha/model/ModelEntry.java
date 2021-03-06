package tk.kahsolt.akasha.model;

import java.lang.annotation.*;

@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface ModelEntry {

    /*
     *  用来制定模型类建表时的一些参数(对MySQL有效)
     */

    /*
     *  设置数据库引擎
     */
    String engine() default "MyISAM";

    /*
     *  设全表默认字符集
     */
    String charset() default "utf8";

    /*
     *  设置表注释
     */
    String comment() default "";
}
