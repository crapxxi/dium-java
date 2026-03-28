package com.dium.demo.mappers;

import com.dium.demo.dto.product_modifier.ModifierDTO;
import com.dium.demo.models.Modifier;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

import java.util.List;

@Mapper(componentModel = "spring")
public interface ModifierMapper {
    ModifierDTO toDto(Modifier modifier);

    List<ModifierDTO> toDtoList(List<Modifier> modifiers);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "modifierGroup", ignore = true)
    Modifier toEntity(ModifierDTO modifierDTO);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "modifierGroup", ignore = true)
    void updateFromDto(ModifierDTO modifierDTO, @MappingTarget Modifier entity);
}
