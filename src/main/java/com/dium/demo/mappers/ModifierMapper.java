package com.dium.demo.mappers;

import com.dium.demo.dto.requests.ModifierRequest;
import com.dium.demo.dto.responses.ModifierResponse;
import com.dium.demo.models.Modifier;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

import java.util.List;

@Mapper(componentModel = "spring")
public interface ModifierMapper {
    ModifierResponse toResponse(Modifier modifier);

    List<ModifierResponse> toResponseList(List<Modifier> modifiers);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "modifierGroup", ignore = true)
    Modifier toEntity(ModifierRequest request);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "modifierGroup", ignore = true)
    void updateFromRequest(ModifierRequest request, @MappingTarget Modifier entity);
}
