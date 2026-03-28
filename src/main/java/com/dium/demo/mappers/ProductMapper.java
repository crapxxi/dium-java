package com.dium.demo.mappers;

import com.dium.demo.dto.venue_product.ProductDTO;
import com.dium.demo.models.Product;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

import java.util.List;

@Mapper(componentModel = "spring")
public interface ProductMapper {
    ProductDTO toDto(Product product);

    List<ProductDTO> toDtoList(List<Product> products);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "venue", ignore = true)
    @Mapping(target = "modifierGroups", ignore = true)
    Product toEntity(ProductDTO productDto);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "venue", ignore = true)
    @Mapping(target = "imageUrl", ignore = true)
    @Mapping(target = "modifierGroups", ignore = true)
    void updateProductFromDto(ProductDTO dto, @MappingTarget Product entity);
}
