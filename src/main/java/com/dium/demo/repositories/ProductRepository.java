package com.dium.demo.repositories;

import com.dium.demo.models.Product;
import com.dium.demo.security.access.OwnerIdRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProductRepository extends BaseRepository<Product, Long>, OwnerIdRepository {
    List<Product> findAllByVenueIdAndDeletedFalse(Long venueId);

    @Override
    @Query("select p.venue.owner.id from Product p where p.id = :id")
    Optional<Long> findOwnerIdById(@Param("id") Long id);
}
