package com.dium.demo.repositories;

import com.dium.demo.models.ModifierGroup;
import com.dium.demo.security.access.OwnerIdRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ModifierGroupRepository extends BaseRepository<ModifierGroup, Long>, OwnerIdRepository {
    List<ModifierGroup> findAllByProductIdAndIsDeletedFalse(Long productId);

    @Override
    @Query("select g.product.venue.owner.id from ModifierGroup g where g.id = :id")
    Optional<Long> findOwnerIdById(@Param("id") Long id);
}
