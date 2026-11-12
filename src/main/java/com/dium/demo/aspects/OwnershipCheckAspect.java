package com.dium.demo.aspects;

import com.dium.demo.exceptions.AccessDeniedException;
import com.dium.demo.security.access.CheckOwnership;
import com.dium.demo.security.access.OwnedResource;
import com.dium.demo.security.access.OwnerLookup;
import com.dium.demo.services.auth.CustomUserDetailsService;
import jakarta.persistence.EntityNotFoundException;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Проверяет, что ресурс из {@link CheckOwnership} принадлежит заведению текущего пользователя.
 * Сам аспект не знает, как искать владельца: это делает {@link OwnerLookup} нужного типа.
 */
@Aspect
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class OwnershipCheckAspect {
    private final CustomUserDetailsService userDetailsService;
    private final Map<OwnedResource, OwnerLookup> lookups = new EnumMap<>(OwnedResource.class);

    public OwnershipCheckAspect(CustomUserDetailsService userDetailsService, List<OwnerLookup> lookups) {
        this.userDetailsService = userDetailsService;
        lookups.forEach(lookup -> this.lookups.put(lookup.resource(), lookup));
    }

    @Before("@annotation(checkOwnership)")
    public void checkOwnership(JoinPoint joinPoint, CheckOwnership checkOwnership) {
        OwnedResource resource = checkOwnership.value();
        Long resourceId = resourceId(joinPoint, checkOwnership);
        Long currentUserId = userDetailsService.getCurrentUser().getId();

        Long ownerId = lookupFor(resource).findOwnerId(resourceId)
                .orElseThrow(() -> new EntityNotFoundException(resource.getLabel() + " not found"));

        if (!ownerId.equals(currentUserId)) {
            throw new AccessDeniedException(
                    "Access denied: " + resource.getLabel().toLowerCase() + " belongs to another venue");
        }
    }

    private OwnerLookup lookupFor(OwnedResource resource) {
        OwnerLookup lookup = lookups.get(resource);
        if (lookup == null) {
            throw new IllegalStateException("No OwnerLookup registered for " + resource);
        }
        return lookup;
    }

    private Long resourceId(JoinPoint joinPoint, CheckOwnership checkOwnership) {
        Object[] args = joinPoint.getArgs();
        int index = checkOwnership.idArgIndex();

        if (index < 0 || index >= args.length || !(args[index] instanceof Long id)) {
            throw new IllegalStateException("@CheckOwnership: argument #" + index + " of "
                    + joinPoint.getSignature().toShortString() + " must be a non-null Long id");
        }
        return id;
    }
}
