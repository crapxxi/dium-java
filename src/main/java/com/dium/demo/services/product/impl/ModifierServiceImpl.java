package com.dium.demo.services.product.impl;

import com.dium.demo.dto.requests.ModifierRequest;
import com.dium.demo.dto.responses.ModifierResponse;
import com.dium.demo.enums.UserRole;
import com.dium.demo.exceptions.AccessDeniedException;
import com.dium.demo.exceptions.BusinessLogicException;
import com.dium.demo.mappers.ModifierMapper;
import com.dium.demo.models.Modifier;
import com.dium.demo.models.User;
import com.dium.demo.repositories.ModifierRepository;
import com.dium.demo.services.auth.CustomUserDetailsService;
import com.dium.demo.services.product.ModifierService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class ModifierServiceImpl implements ModifierService {
    private final ModifierRepository modifierRepository;
    private final ModifierMapper modifierMapper;
    private final CustomUserDetailsService userDetailsService;


    @Transactional
    public ModifierResponse editModifier(Long modifierId, ModifierRequest request) {
        User user = userDetailsService.getCurrentUser();

        checkVenueOwner(user);

        Modifier modifier = modifierRepository.findById(modifierId)
                .orElseThrow(() -> new EntityNotFoundException("Modifier not found"));

        checkAccess(user, modifier);

        validatePriceDelta(request.priceDelta());

        modifierMapper.updateFromRequest(request, modifier);

        return modifierMapper.toResponse(modifierRepository.save(modifier));
    }


    @Transactional
    public void delete(Long modifierId) {
        User user = userDetailsService.getCurrentUser();
        checkVenueOwner(user);

        Modifier modifier = modifierRepository.findById(modifierId)
                .orElseThrow(() -> new EntityNotFoundException("Modifier not found"));

        checkAccess(user, modifier);

        modifierRepository.delete(modifier);
    }



    @Transactional
    public void toggleStock(Long modifierId) {
        User user = userDetailsService.getCurrentUser();
        checkVenueOwner(user);
        Modifier modifier = modifierRepository.findById(modifierId)
                .orElseThrow(() -> new EntityNotFoundException("Modifier not found"));

        checkAccess(user, modifier);

        boolean currentStock = modifier.getInStock() != null && modifier.getInStock();
        modifier.setInStock(!currentStock);

        modifierRepository.save(modifier);
    }


    private void validatePriceDelta(BigDecimal priceDelta) {
        if (priceDelta == null) return;
        if (priceDelta.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessLogicException("Price delta is out of allowed range (too big discount)");
        }
    }

    private void checkVenueOwner(User user) {
        if(user.getRole() != UserRole.VENUE_OWNER)
            throw new AccessDeniedException("Access denied: Not a venue owner");
    }

    private void checkAccess(User user, Modifier modifier) {
        if(!modifier.getModifierGroup().getProduct().getVenue().getOwner().getId().equals(user.getId()))
            throw new AccessDeniedException("Access denied: venue doesn't own this modifier");
    }
}