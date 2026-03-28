package com.dium.demo.mappers;

import com.dium.demo.dto.order.OrderResponse;
import com.dium.demo.models.Order;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

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
    @Mapping(target = "items", ignore = true)
    Order toEntity(OrderResponse orderResponse);
}
