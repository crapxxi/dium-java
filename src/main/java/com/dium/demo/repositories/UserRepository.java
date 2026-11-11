package com.dium.demo.repositories;

import com.dium.demo.models.User;

import java.util.Optional;

public interface UserRepository extends BaseRepository<User, Long> {
    Optional<User> findByPhone(String phone);
    Boolean existsByPhone(String phone);
}
