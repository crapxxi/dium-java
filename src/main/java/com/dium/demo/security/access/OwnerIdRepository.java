package com.dium.demo.security.access;

import java.util.Optional;

/** Репозиторий ресурса, умеющий одним запросом вернуть id владельца заведения. */
public interface OwnerIdRepository {
    Optional<Long> findOwnerIdById(Long id);
}
