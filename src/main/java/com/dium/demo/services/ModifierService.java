package com.dium.demo.services;

import com.dium.demo.dto.product_modifier.ModifierDTO;
import com.dium.demo.dto.product_modifier.ModifierGroupDTO;
import com.dium.demo.enums.UserRole;
import com.dium.demo.mappers.ModifierGroupMapper;
import com.dium.demo.mappers.ModifierMapper;
import com.dium.demo.models.Modifier;
import com.dium.demo.models.ModifierGroup;
import com.dium.demo.models.Product;
import com.dium.demo.models.User;
import com.dium.demo.repositories.ModifierGroupRepository;
import com.dium.demo.repositories.ModifierRepository;
import com.dium.demo.repositories.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
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

    private static final BigDecimal MAX_DISCOUNT = BigDecimal.ZERO;

    @Transactional
    public ModifierGroupDTO addModifierGroup(UserDetails userDetails, Long productId, ModifierGroupDTO modifierGroupDTO) {
        User user = checkVenueOwner(userDetails);

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new RuntimeException("Product not found"));

        if(!user.getId().equals(product.getVenue().getOwner().getId())) {
            throw new RuntimeException("You cannot add modifier group to this product");
        }

        ModifierGroup modifierGroup = modifierGroupMapper.toEntity(modifierGroupDTO);
        modifierGroup.setProduct(product);
        modifierGroup.setIsDeleted(false);

        if(modifierGroup.getModifiers() != null) {
            modifierGroup.getModifiers().forEach(modifier -> {
                validatePriceDelta(modifier.getPriceDelta());
                modifier.setModifierGroup(modifierGroup);
                modifier.setInStock(true);
            });
        }

        return modifierGroupMapper.toDto(modifierGroupRepository.save(modifierGroup));
    }

    @Transactional
    public ModifierDTO editModifier(UserDetails userDetails, Long modifierId, ModifierDTO modifierDTO) {
        User user = checkVenueOwner(userDetails);

        Modifier modifier = modifierRepository.findById(modifierId)
                .orElseThrow(() -> new RuntimeException("Modifier not found"));

        checkAccess(user, modifier);

        validatePriceDelta(modifierDTO.priceDelta());

        modifierMapper.updateFromDto(modifierDTO, modifier);

        return modifierMapper.toDto(modifierRepository.save(modifier));
    }

    @Transactional
    public ModifierGroupDTO editModifierGroup(UserDetails userDetails, Long modifierGroupId, ModifierGroupDTO modifierGroupDTO) {
        User user = checkVenueOwner(userDetails);

        ModifierGroup modifierGroup = modifierGroupRepository.findById(modifierGroupId)
                .orElseThrow(() -> new RuntimeException("Modifier group not found"));

        checkAccess(user, modifierGroup);

        Product originalProduct = modifierGroup.getProduct();

        modifierGroupMapper.updateFromDto(modifierGroupDTO, modifierGroup);

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

        return modifierGroupMapper.toDto(modifierGroupRepository.save(modifierGroup));
    }

    @Transactional
    public void delete(UserDetails userDetails, Long modifierId) {
        User user = checkVenueOwner(userDetails);
        Modifier modifier = modifierRepository.findById(modifierId)
                .orElseThrow(() -> new RuntimeException("Modifier not found"));

        checkAccess(user, modifier);

        modifierRepository.delete(modifier);
    }
    @Transactional
    public void deleteModifierGroup(UserDetails userDetails, Long modifierGroupId) {
        User user = checkVenueOwner(userDetails);

        ModifierGroup modifierGroup = modifierGroupRepository.findById(modifierGroupId)
                .orElseThrow(() -> new RuntimeException("Modifier group not found"));

        checkAccess(user, modifierGroup);

        modifierGroup.setIsDeleted(true);

        modifierGroupRepository.save(modifierGroup);
    }

    @Transactional
    public void toggleRequired(UserDetails userDetails, Long modifierGroupId) {
        User user = checkVenueOwner(userDetails);

        ModifierGroup modifierGroup = modifierGroupRepository.findById(modifierGroupId)
                .orElseThrow(() -> new RuntimeException("Modifier group not found"));

        checkAccess(user, modifierGroup);

        boolean currentRequired = modifierGroup.getRequired() != null && modifierGroup.getRequired();
        modifierGroup.setRequired(!currentRequired);

        modifierGroupRepository.save(modifierGroup);
    }

    @Transactional
    public void toggleStock(UserDetails userDetails, Long modifierId) {
        User user = checkVenueOwner(userDetails);
        Modifier modifier = modifierRepository.findById(modifierId)
                .orElseThrow(() -> new RuntimeException("Modifier not found"));

        checkAccess(user, modifier);

        boolean currentStock = modifier.getInStock() != null && modifier.getInStock();
        modifier.setInStock(!currentStock);

        modifierRepository.save(modifier);
    }

    @Transactional(readOnly = true)
    public List<ModifierGroupDTO> getModifierGroups(Long productId) {
        List<ModifierGroup> modifierGroups = modifierGroupRepository.findAllByProductIdAndIsDeletedFalse(productId);

        return modifierGroupMapper.toDtoList(modifierGroups);
    }



    private void validatePriceDelta(BigDecimal priceDelta) {
        if (priceDelta == null) return;
        if (priceDelta.compareTo(MAX_DISCOUNT) < 0) {
            throw new RuntimeException("Price delta is out of allowed range (too big discount)");
        }
    }

    public User checkVenueOwner(UserDetails userDetails) {
        if(!(userDetails instanceof User user) || user.getRole() != UserRole.VENUE_OWNER)
            throw new RuntimeException("Access denied: Not a venue owner");
        return user;
    }

    public void checkAccess(User user, Modifier modifier) {
        if(!modifier.getModifierGroup().getProduct().getVenue().getOwner().getId().equals(user.getId()))
            throw new RuntimeException("Access denied: You don't own this modifier");
    }

    public void checkAccess(User user, ModifierGroup modifierGroup) {
        if(!modifierGroup.getProduct().getVenue().getOwner().getId().equals(user.getId()))
            throw new RuntimeException("Access denied: You don't own this group");
    }
}