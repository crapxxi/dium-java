package com.dium.demo.security.access;

import java.util.Optional;

/**
 * Находит владельца ресурса одного типа. Чтобы защитить новый тип ресурса, достаточно
 * добавить значение в {@link OwnedResource} и зарегистрировать ещё один бин этого интерфейса.
 */
public interface OwnerLookup {
    OwnedResource resource();

    /** @return id владельца заведения, которому принадлежит ресурс; пусто, если ресурса нет */
    Optional<Long> findOwnerId(Long resourceId);

    static OwnerLookup of(OwnedResource resource, OwnerIdRepository repository) {
        return new OwnerLookup() {
            @Override
            public OwnedResource resource() {
                return resource;
            }

            @Override
            public Optional<Long> findOwnerId(Long resourceId) {
                return repository.findOwnerIdById(resourceId);
            }
        };
    }
}
