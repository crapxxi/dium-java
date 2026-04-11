package com.dium.demo.mappers;

import com.dium.demo.dto.requests.ModifierGroupRequest;
import com.dium.demo.dto.responses.ModifierGroupResponse;
import com.dium.demo.models.ModifierGroup;
import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

import java.util.List;

@Mapper(componentModel = "spring", uses = ModifierMapper.class)
public interface ModifierGroupMapper {
    ModifierGroupResponse toResponse(ModifierGroup modifierGroup);

    List<ModifierGroupResponse> toResponseList(List<ModifierGroup> modifierGroups);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "product", ignore = true)
    ModifierGroup toEntity(ModifierGroupRequest request);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "product", ignore = true)
    void updateFromRequest(ModifierGroupRequest request, @MappingTarget ModifierGroup entity);

    @AfterMapping
    default void linkModifiers(@MappingTarget ModifierGroup modifierGroup) {
        if(modifierGroup.getModifiers()!= null)
            modifierGroup.getModifiers().forEach(modifier -> modifier.setModifierGroup(modifierGroup));
    }
}
