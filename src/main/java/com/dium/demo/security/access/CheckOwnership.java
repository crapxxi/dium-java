package com.dium.demo.security.access;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Метод доступен только владельцу заведения, которому принадлежит ресурс.
 * Проверку выполняет {@link com.dium.demo.aspects.OwnershipCheckAspect}.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface CheckOwnership {
    /** Тип ресурса, владельца которого нужно проверить. */
    OwnedResource value();

    /** Индекс аргумента метода с идентификатором ресурса (тип {@code Long}). */
    int idArgIndex() default 0;
}
