package com.gogoga.backend.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 权限校验注解
 * 标注此注解的方法或类需要拥有指定权限才能访问
 *
 * @author system
 * @since 2025-01-21
 */
@Documented
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequiresPermission {

    /**
     * 权限标识
     */
    String value();

    /**
     * 逻辑模式，默认 AND（需要全部满足）
     */
    Logical logical() default Logical.AND;

    /**
     * 逻辑枚举
     */
    enum Logical {
        /**
         * 需要全部权限
         */
        AND,
        /**
         * 满足其一即可
         */
        OR
    }
}
