package com.volunteer.common.annotation;

import java.lang.annotation.*;

/**
 * #15 操作审计日志 - 自定义日志注解
 * <p>标记需要记录操作审计的方法。</p>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Log {

    /** 操作类型：CREATE/UPDATE/DELETE/QUERY/LOGIN/EXPORT/APPROVE */
    String opType() default "QUERY";

    /** 操作模块 */
    String opModule() default "default";

    /** 操作内容描述，支持 SpEL 表达式 */
    String opContent() default "";
}