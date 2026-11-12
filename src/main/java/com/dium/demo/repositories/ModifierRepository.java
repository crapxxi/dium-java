package com.dium.demo.repositories;

import com.dium.demo.models.Modifier;
import com.dium.demo.security.access.OwnerIdRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ModifierRepository extends BaseRepository<Modifier, Long>, OwnerIdRepository {

    @Override
    @Query("select m.modifierGroup.product.venue.owner.id from Modifier m where m.id = :id")
    Optional<Long> findOwnerIdById(@Param("id") Long id);
}
