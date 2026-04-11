package com.dium.demo.services;

import com.dium.demo.dto.requests.ModifierGroupRequest;
import com.dium.demo.dto.requests.ModifierRequest;
import com.dium.demo.dto.responses.ModifierGroupResponse;
import com.dium.demo.dto.responses.ModifierResponse;
import com.dium.demo.enums.UserRole;
import com.dium.demo.exceptions.AccessDeniedException;
import com.dium.demo.exceptions.BusinessLogicException;
import com.dium.demo.mappers.ModifierGroupMapper;
import com.dium.demo.mappers.ModifierMapper;
import com.dium.demo.models.Modifier;
import com.dium.demo.models.ModifierGroup;
import com.dium.demo.models.Product;
import com.dium.demo.models.User;
import com.dium.demo.repositories.ModifierGroupRepository;
import com.dium.demo.repositories.ModifierRepository;
import com.dium.demo.repositories.ProductRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ModifierService {
    private final ProductRepository productRepository;
    private final ModifierRepository modifierRepository;
    private final ModifierGroupRepository modifierGroupRepository;
    private final ModifierGroupMapper modifierGroupMapper;
    private final ModifierMapper modifierMapper;
    private final CustomUserDetailsService userDetailsService;


    @Transactional
    public ModifierGroupResponse addModifierGroup(Long productId, ModifierGroupRequest request) {
        User user = userDetailsService.getCurrentUser();
        checkVenueOwner(user);

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new EntityNotFoundException("Product not found"));

        if(!user.getId().equals(product.getVenue().getOwner().getId()))
            throw new AccessDeniedException("You cannot add modifier group to this product");

        ModifierGroup modifierGroup = modifierGroupMapper.toEntity(request);
        modifierGroup.setProduct(product);
        modifierGroup.setIsDeleted(false);

        if(modifierGroup.getModifiers() != null) {
            modifierGroup.getModifiers().forEach(modifier -> {
                validatePriceDelta(modifier.getPriceDelta());
                modifier.setModifierGroup(modifierGroup);
                modifier.setInStock(true);
            });
        }

        return modifierGroupMapper.toResponse( modifierGroupRepository.save(modifierGroup) );
    }

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
    public ModifierGroupResponse editModifierGroup(Long modifierGroupId, ModifierGroupRequest request) {
        User user = userDetailsService.getCurrentUser();

        checkVenueOwner(user);

        ModifierGroup modifierGroup = modifierGroupRepository.findById(modifierGroupId)
                .orElseThrow(() -> new EntityNotFoundException("Modifier group not found"));

        checkAccess(user, modifierGroup);

        Product originalProduct = modifierGroup.getProduct();

        modifierGroupMapper.updateFromRequest(request, modifierGroup);

        modifierGroup.setProduct(originalProduct);
        modifierGroup.setIsDeleted(false);

        if(modifierGroup.getModifiers() != null) {
            modifierGroup.getModifiers().forEach(modifier -> {
                validatePriceDelta(modifier.getPriceDelta());
                if (modifier.getModifierGroup() == null) {
                    modifier.setModifierGroup(modifierGroup);
                }
            });
        }

        return modifierGroupMapper.toResponse(modifierGroupRepository.save(modifierGroup));
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
    public void deleteModifierGroup(Long modifierGroupId) {
        User user = userDetailsService.getCurrentUser();
        checkVenueOwner(user);

        ModifierGroup modifierGroup = modifierGroupRepository.findById(modifierGroupId)
                .orElseThrow(() -> new EntityNotFoundException("Modifier group not found"));

        checkAccess(user, modifierGroup);

        modifierGroup.setIsDeleted(true);

        modifierGroupRepository.save(modifierGroup);
    }

    @Transactional
    public void toggleRequired(Long modifierGroupId) {
        User user = userDetailsService.getCurrentUser();

        checkVenueOwner(user);

        ModifierGroup modifierGroup = modifierGroupRepository.findById(modifierGroupId)
                .orElseThrow(() -> new EntityNotFoundException("Modifier group not found"));

        checkAccess(user, modifierGroup);

        boolean currentRequired = modifierGroup.getRequired() != null && modifierGroup.getRequired();
        modifierGroup.setRequired(!currentRequired);

        modifierGroupRepository.save(modifierGroup);
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

    @Transactional(readOnly = true)
    public List<ModifierGroupResponse> getModifierGroups(Long productId) {
        List<ModifierGroup> modifierGroups = modifierGroupRepository.findAllByProductIdAndIsDeletedFalse(productId);

        return modifierGroupMapper.toResponseList(modifierGroups);
    }



    private void validatePriceDelta(BigDecimal priceDelta) {
        if (priceDelta == null) return;
        if (priceDelta.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessLogicException("Price delta is out of allowed range (too big discount)");
        }
    }

    public void checkVenueOwner(User user) {
        if(user.getRole() != UserRole.VENUE_OWNER)
            throw new AccessDeniedException("Access denied: Not a venue owner");
    }

    public void checkAccess(User user, Modifier modifier) {
        if(!modifier.getModifierGroup().getProduct().getVenue().getOwner().getId().equals(user.getId()))
            throw new AccessDeniedException("Access denied: venue doesn't own this modifier");
    }

    public void checkAccess(User user, ModifierGroup modifierGroup) {
        if(!modifierGroup.getProduct().getVenue().getOwner().getId().equals(user.getId()))
            throw new AccessDeniedException("Access denied: venue doesn't own this group");
    }
}