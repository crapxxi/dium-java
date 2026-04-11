package com.dium.demo.mappers;

import com.dium.demo.dto.requests.OrderItemRequest;
import com.dium.demo.dto.responses.OrderItemResponse;
import com.dium.demo.models.OrderItem;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface OrderItemMapper {
    @Mapping(source = "product.name", target = "productName")
    OrderItemResponse toResponse(OrderItem orderItem);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "product", ignore = true)
    @Mapping(target = "order", ignore = true)
    @Mapping(target = "modifiers", ignore = true)
    OrderItem toEntity(OrderItemRequest request);
}
