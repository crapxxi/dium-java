package com.dium.demo.repositories;

import com.dium.demo.enums.ProductCategory;
import com.dium.demo.models.Product;

import java.util.List;

public interface ProductRepository extends BaseRepository<Product, Long> {
    List<Product> findAllByVenueIdAndDeletedFalse(Long venueId);

}
