package com.dium.demo.repositories;

import com.dium.demo.models.Modifier;
import com.dium.demo.models.ModifierGroup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ModifierRepository extends BaseRepository<Modifier, Long> {
    List<Modifier> findAllByModifierGroupId(Long modifierGroupId);
}
