package com.dium.demo.mappers;

import com.dium.demo.dto.venue_product.VenueDTO;
import com.dium.demo.models.Venue;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

import java.util.List;

@Mapper(componentModel = "spring")
public interface VenueMapper {
    VenueDTO toDto(Venue venue);
    List<VenueDTO> toDtoList(List<Venue> venues);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "owner", ignore = true)
    @Mapping(target = "products", ignore = true)
    Venue toEntity(VenueDTO venueDto);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "owner", ignore = true)
    @Mapping(target = "products", ignore = true)
    void updateVenueFromDto(VenueDTO dto, @MappingTarget Venue entity);
}
