package com.volunteer.auth.annotation;

import java.lang.annotation.*;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequiresAccess {
    
    String value() default "visitor";
    
    boolean requireStudentId() default false;
}
