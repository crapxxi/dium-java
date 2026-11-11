package com.dium.demo.services.product;

import com.dium.demo.dto.requests.ModifierGroupRequest;
import com.dium.demo.dto.responses.ModifierGroupResponse;

import java.util.List;

public interface ModifierGroupService {
    ModifierGroupResponse addModifierGroup(Long productId, ModifierGroupRequest request);
    List<ModifierGroupResponse> getModifierGroups(Long productId);
    ModifierGroupResponse editModifierGroup(Long modifierGroupId, ModifierGroupRequest request);
    void deleteModifierGroup(Long modifierGroupId);
    void toggleRequired(Long modifierGroupId);

}
