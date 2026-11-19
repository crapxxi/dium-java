package com.dium.demo.repositories;

import com.dium.demo.models.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserRepository extends BaseRepository<User, Long> {
    Optional<User> findByPhone(String phone);
    Boolean existsByPhone(String phone);

    /**
     * Блокирует строку пользователя до конца транзакции (SELECT ... FOR UPDATE).
     * Нужна, чтобы параллельные запросы одного курьера не обошли лимит активных доставок.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.id = :id")
    Optional<User> findByIdForUpdate(@Param("id") Long id);
}
