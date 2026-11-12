package com.dium.demo.security.access;

import com.dium.demo.enums.UserRole;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Метод доступен только текущему пользователю с одной из перечисленных ролей.
 * Проверку выполняет {@link com.dium.demo.aspects.RoleCheckAspect}.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequireRole {
    UserRole[] value();
}
