package com.dium.demo.mappers;

import com.dium.demo.config.DeliveryProperties;
import com.dium.demo.dto.requests.VenueRequest;
import com.dium.demo.dto.responses.VenueResponse;
import com.dium.demo.models.Venue;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

/**
 * Абстрактный класс, а не интерфейс: в ответ подставляется единая цена доставки из настроек
 * (доступна ли доставка в заведении, показывает {@code canDeliver}).
 * Цена, которую раньше задавало заведение ({@code venues.delivery_price}), покупателю не показывается
 * и в заказе не используется.
 */
@Mapper(config = EntityMapperConfig.class)
public abstract class VenueMapper {
    @Autowired
    protected DeliveryProperties deliveryProperties;

    @Mapping(target = "deliveryPrice", expression = "java(deliveryProperties.fee())")
    public abstract VenueResponse toResponse(Venue venue);

    public abstract List<VenueResponse> toResponseList(List<Venue> venues);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "owner", ignore = true)
    @Mapping(target = "products", ignore = true)
    @Mapping(target = "imageUrl", ignore = true)
    @Mapping(target = "deliveryPrice", ignore = true)
    public abstract Venue toEntity(VenueRequest request);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "owner", ignore = true)
    @Mapping(target = "products", ignore = true)
    @Mapping(target = "imageUrl", ignore = true)
    @Mapping(target = "deliveryPrice", ignore = true)
    public abstract void updateVenueFromRequest(VenueRequest request, @MappingTarget Venue entity);
}
