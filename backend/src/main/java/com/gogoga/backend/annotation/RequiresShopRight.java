package com.gogoga.backend.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 门店权益校验注解
 *
 * @author system
 * @since 2026-03-25
 */
@Documented
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequiresShopRight {

    /**
     * 权益编码列表
     */
    String[] value();

    /**
     * 逻辑模式
     */
    Logical logical() default Logical.OR;

    /**
     * 逻辑枚举
     */
    enum Logical {
        /**
         * 需要全部权益
         */
        AND,
        /**
         * 命中任意权益即可
         */
        OR
    }
}
