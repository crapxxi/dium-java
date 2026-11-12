package com.dium.demo.aspects;

import com.dium.demo.enums.UserRole;
import com.dium.demo.exceptions.AccessDeniedException;
import com.dium.demo.security.access.RequireRole;
import com.dium.demo.services.auth.CustomUserDetailsService;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Arrays;

/**
 * Проверяет, что вызов выполняет аутентифицированный пользователь с ролью из {@link RequireRole}.
 * Выполняется раньше проверки владения и раньше открытия транзакции.
 */
@Aspect
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
@RequiredArgsConstructor
public class RoleCheckAspect {
    private final CustomUserDetailsService userDetailsService;

    @Before("@annotation(requireRole)")
    public void checkRole(RequireRole requireRole) {
        UserRole actual = userDetailsService.getCurrentUser().getRole();
        UserRole[] allowed = requireRole.value();

        if (!Arrays.asList(allowed).contains(actual)) {
            throw new AccessDeniedException("Access denied: required role " + Arrays.toString(allowed));
        }
    }
}
