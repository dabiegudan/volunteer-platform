package com.volunteer.auth.annotation;

import java.lang.annotation.*;

/**
 * 细粒度权限注解(R-01 实施)
 * <p>
 * 用于替代旧的 @RequiresAccess("admin"/"organizer") 字符串硬编码方案。
 * 拦截器在执行前会读取当前用户的权限码集合,命中任意一个 value 即放行。
 * </p>
 *
 * <pre>
 *   &#64;RequiresPermission("system:user")
 *   public Result&lt;Page&lt;SysUser&gt;&gt; page(...) { ... }
 * </pre>
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequiresPermission {

    /**
     * 任意一个权限码匹配即放行(OR 语义)
     */
    String[] value() default {};

    /**
     * 是否要求用户已绑定学号(FULL_ACCESS)
     */
    boolean requireStudentId() default false;
}
