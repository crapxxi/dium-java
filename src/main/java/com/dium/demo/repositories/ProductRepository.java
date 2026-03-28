package com.dium.demo.repositories;

import com.dium.demo.enums.ProductCategory;
import com.dium.demo.models.Product;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProductRepository extends JpaRepository<Product, Long> {
    List<Product> findAllByVenueIdAndDeletedFalse(Long venueId);
    List<Product> findAllByVenueIdAndCategory(Long venueId, ProductCategory category);

}
