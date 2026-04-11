package com.dium.demo.mappers;

import com.dium.demo.dto.requests.ProductRequest;
import com.dium.demo.dto.responses.ProductResponse;
import com.dium.demo.models.Product;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

import java.util.List;

@Mapper(componentModel = "spring")
public interface ProductMapper {
    ProductResponse toResponse(Product product);

    List<ProductResponse> toResponseList(List<Product> products);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "venue", ignore = true)
    @Mapping(target = "modifierGroups", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    Product toEntity(ProductRequest request);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "venue", ignore = true)
    @Mapping(target = "imageUrl", ignore = true)
    @Mapping(target = "modifierGroups", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    void updateProductFromRequest(ProductRequest request, @MappingTarget Product entity);
}
