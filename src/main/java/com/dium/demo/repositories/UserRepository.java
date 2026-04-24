package com.dium.demo.repositories;

import com.dium.demo.models.ModifierGroup;
import com.dium.demo.models.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends BaseRepository<User, Long> {
    Optional<User> findByPhone(String phone);
    Boolean existsByPhone(String phone);
}
