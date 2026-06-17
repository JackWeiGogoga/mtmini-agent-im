package com.gogoga.backend.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 登录校验注解
 * 标注此注解的方法或类需要登录后才能访问
 *
 * @author system
 * @since 2025-01-21
 */
@Documented
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequiresLogin {

    /**
     * 是否必须登录，默认 true
     */
    boolean required() default true;
}
