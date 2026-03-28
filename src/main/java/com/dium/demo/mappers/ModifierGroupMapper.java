package com.dium.demo.mappers;

import com.dium.demo.dto.product_modifier.ModifierGroupDTO;
import com.dium.demo.models.ModifierGroup;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

import java.util.List;

@Mapper(componentModel = "spring", uses = ModifierMapper.class)
public interface ModifierGroupMapper {
    ModifierGroupDTO toDto(ModifierGroup modifierGroup);

    List<ModifierGroupDTO> toDtoList(List<ModifierGroup> modifierGroups);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "product", ignore = true)
    ModifierGroup toEntity(ModifierGroupDTO modifierGroupDTO);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "product", ignore = true)
    void updateFromDto(ModifierGroupDTO modifierGroupDTO, @MappingTarget ModifierGroup entity);
}
