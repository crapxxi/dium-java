package com.dium.demo.mappers;

import com.dium.demo.dto.requests.OrderRequest;
import com.dium.demo.dto.responses.OrderResponse;
import com.dium.demo.models.Order;
import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

import java.util.List;

@Mapper(componentModel = "spring",uses = {OrderItemMapper.class})
public interface OrderMapper {

    @Mapping(source = "venue.name", target = "venueName")
    OrderResponse toResponse(Order order);

    @Mapping(source = "venue.name", target = "venueName")
    List<OrderResponse> toResponseList(List<Order> orders);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "user", ignore = true)
    @Mapping(target = "venue", ignore = true)
    @Mapping(target = "status", ignore = true)
    Order toEntity(OrderRequest request);

    @AfterMapping
    default void linkOrderItems(@MappingTarget Order order) {
        if (order.getItems() != null) {
            order.getItems().forEach(item -> item.setOrder(order));
        }
    }
}
