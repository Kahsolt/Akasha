package tk.kahsolt.akasha.model;

import java.lang.annotation.*;

@Documented
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ManagerEntry {

    /*
     *  用来修饰模型管理器（由Hypnos自动注入），请定义为：
     *    @ManagerEntry
     *    public static Manager objects;    // 字段名也可自拟，例如manager
     *
     *  如果没有显式定义此注解的字段，也可通过模型类的实例来获取管理器，例如：
     *    Manager objects = new User().objects();
     *
     */

}
