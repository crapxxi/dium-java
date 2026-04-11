package com.dium.demo.repositories;

import com.dium.demo.models.ModifierGroup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ModifierGroupRepository extends BaseRepository<ModifierGroup, Long> {
    List<ModifierGroup> findAllByProductIdAndIsDeletedFalse(Long productId);
}
