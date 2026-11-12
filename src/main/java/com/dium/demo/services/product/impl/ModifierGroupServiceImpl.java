package com.dium.demo.services.product.impl;

import com.dium.demo.dto.requests.ModifierGroupRequest;
import com.dium.demo.dto.responses.ModifierGroupResponse;
import com.dium.demo.enums.UserRole;
import com.dium.demo.mappers.ModifierGroupMapper;
import com.dium.demo.models.Modifier;
import com.dium.demo.models.ModifierGroup;
import com.dium.demo.repositories.ModifierGroupRepository;
import com.dium.demo.repositories.ProductRepository;
import com.dium.demo.security.access.CheckOwnership;
import com.dium.demo.security.access.OwnedResource;
import com.dium.demo.security.access.RequireRole;
import com.dium.demo.services.product.ModifierGroupService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ModifierGroupServiceImpl implements ModifierGroupService {
    private final ModifierGroupRepository modifierGroupRepository;
    private final ModifierGroupMapper modifierGroupMapper;
    private final ProductRepository productRepository;

    @RequireRole(UserRole.VENUE_OWNER)
    @CheckOwnership(OwnedResource.PRODUCT)
    @Transactional
    @Override
    public ModifierGroupResponse addModifierGroup(Long productId, ModifierGroupRequest request) {
        ModifierGroup modifierGroup = modifierGroupMapper.toEntity(request);
        modifierGroup.setProduct(productRepository.findByIdOrThrow(productId));
        modifierGroup.setIsDeleted(false);

        attachModifiers(modifierGroup);
        modifiersOf(modifierGroup).forEach(modifier -> modifier.setInStock(true));

        return modifierGroupMapper.toResponse(modifierGroupRepository.save(modifierGroup));
    }

    @Transactional(readOnly = true)
    @Override
    public List<ModifierGroupResponse> getModifierGroups(Long productId) {
        return modifierGroupMapper.toResponseList(
                modifierGroupRepository.findAllByProductIdAndIsDeletedFalse(productId));
    }

    @RequireRole(UserRole.VENUE_OWNER)
    @CheckOwnership(OwnedResource.MODIFIER_GROUP)
    @Transactional
    @Override
    public ModifierGroupResponse editModifierGroup(Long modifierGroupId, ModifierGroupRequest request) {
        ModifierGroup modifierGroup = modifierGroupRepository.findByIdOrThrow(modifierGroupId);

        modifierGroupMapper.updateFromRequest(request, modifierGroup);
        modifierGroup.setIsDeleted(false);
        attachModifiers(modifierGroup);

        return modifierGroupMapper.toResponse(modifierGroupRepository.save(modifierGroup));
    }

    @RequireRole(UserRole.VENUE_OWNER)
    @CheckOwnership(OwnedResource.MODIFIER_GROUP)
    @Transactional
    @Override
    public void toggleRequired(Long modifierGroupId) {
        ModifierGroup modifierGroup = modifierGroupRepository.findByIdOrThrow(modifierGroupId);
        modifierGroup.setRequired(!Boolean.TRUE.equals(modifierGroup.getRequired()));
    }

    @RequireRole(UserRole.VENUE_OWNER)
    @CheckOwnership(OwnedResource.MODIFIER_GROUP)
    @Transactional
    @Override
    public void deleteModifierGroup(Long modifierGroupId) {
        modifierGroupRepository.findByIdOrThrow(modifierGroupId).setIsDeleted(true);
    }

    /** Проверяет наценки и привязывает модификаторы к группе. */
    private void attachModifiers(ModifierGroup modifierGroup) {
        for (Modifier modifier : modifiersOf(modifierGroup)) {
            ModifierPricing.validatePriceDelta(modifier.getPriceDelta());
            modifier.setModifierGroup(modifierGroup);
        }
    }

    private List<Modifier> modifiersOf(ModifierGroup modifierGroup) {
        return modifierGroup.getModifiers() != null ? modifierGroup.getModifiers() : List.of();
    }
}
