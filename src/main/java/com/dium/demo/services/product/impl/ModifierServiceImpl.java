package com.dium.demo.services.product.impl;

import com.dium.demo.dto.requests.ModifierRequest;
import com.dium.demo.dto.responses.ModifierResponse;
import com.dium.demo.enums.UserRole;
import com.dium.demo.mappers.ModifierMapper;
import com.dium.demo.models.Modifier;
import com.dium.demo.repositories.ModifierRepository;
import com.dium.demo.security.access.CheckOwnership;
import com.dium.demo.security.access.OwnedResource;
import com.dium.demo.security.access.RequireRole;
import com.dium.demo.services.product.ModifierService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ModifierServiceImpl implements ModifierService {
    private final ModifierRepository modifierRepository;
    private final ModifierMapper modifierMapper;

    @RequireRole(UserRole.VENUE_OWNER)
    @CheckOwnership(OwnedResource.MODIFIER)
    @Transactional
    @Override
    public ModifierResponse editModifier(Long modifierId, ModifierRequest request) {
        ModifierPricing.validatePriceDelta(request.priceDelta());

        Modifier modifier = modifierRepository.findByIdOrThrow(modifierId);
        modifierMapper.updateFromRequest(request, modifier);

        return modifierMapper.toResponse(modifierRepository.save(modifier));
    }

    @RequireRole(UserRole.VENUE_OWNER)
    @CheckOwnership(OwnedResource.MODIFIER)
    @Transactional
    @Override
    public void delete(Long modifierId) {
        modifierRepository.delete(modifierRepository.findByIdOrThrow(modifierId));
    }

    @RequireRole(UserRole.VENUE_OWNER)
    @CheckOwnership(OwnedResource.MODIFIER)
    @Transactional
    @Override
    public void toggleStock(Long modifierId) {
        Modifier modifier = modifierRepository.findByIdOrThrow(modifierId);
        modifier.setInStock(!Boolean.TRUE.equals(modifier.getInStock()));
    }
}
