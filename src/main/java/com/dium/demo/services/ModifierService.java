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

import java.util.List;

@Service
@RequiredArgsConstructor
public class ModifierService {
    private final ProductRepository productRepository;
    private final ModifierRepository modifierRepository;
    private final ModifierGroupRepository modifierGroupRepository;
    private final ModifierGroupMapper modifierGroupMapper;
    private final ModifierMapper modifierMapper;

    @Transactional
    public ModifierGroupDTO addModifierGroup(UserDetails userDetails, Long productId, ModifierGroupDTO modifierGroupDTO) {
        User user = checkVenueOwner(userDetails);

        System.out.println(modifierGroupDTO);

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new RuntimeException("product not found"));

        if(!user.getId().equals(product.getVenue().getOwner().getId())) {
            throw new RuntimeException("you cannot add modifier group");
        }

        ModifierGroup modifierGroup = modifierGroupMapper.toEntity(modifierGroupDTO);

        modifierGroup.setProduct(product);
        modifierGroup.setIsDeleted(false);

        if(modifierGroup.getModifiers() != null) {
            modifierGroup.getModifiers().forEach(modifier -> modifier.setModifierGroup(modifierGroup));
        }

        return modifierGroupMapper.toDto(modifierGroupRepository.save(modifierGroup));
    }

    @Transactional(readOnly = true)
    public List<ModifierGroupDTO> getModifierGroups(Long productId) {

        List<ModifierGroup> modifierGroups = modifierGroupRepository.findAllByProductIdAndIsDeletedFalse(productId);


        return modifierGroupMapper.toDtoList(modifierGroups);
    }

    @Transactional
    public ModifierDTO editModifier(UserDetails userDetails, Long modifierId, ModifierDTO modifierDTO) {
        User user = checkVenueOwner(userDetails);


        Modifier modifier = modifierRepository.findById(modifierId)
                .orElseThrow(() -> new RuntimeException("modifier not found"));

        checkAccess(user, modifier);

        modifierMapper.updateFromDto(modifierDTO, modifier);

        return modifierMapper.toDto(modifierRepository.save(modifier));
    }

    @Transactional
    public ModifierGroupDTO editModifierGroup(UserDetails userDetails, Long modifierGroupId, ModifierGroupDTO modifierGroupDTO) {
        User user = checkVenueOwner(userDetails);


        ModifierGroup modifierGroup = modifierGroupRepository.findById(modifierGroupId)
                .orElseThrow(() -> new RuntimeException("modifier group not found"));

        checkAccess(user, modifierGroup);


        modifierGroupMapper.updateFromDto(modifierGroupDTO, modifierGroup);
        modifierGroup.setIsDeleted(false);

        if(modifierGroup.getModifiers() != null) {
            modifierGroup.getModifiers().forEach(modifier -> {
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
                .orElseThrow(() -> new RuntimeException("modifier not found"));

        checkAccess(user, modifier);

        modifierRepository.save(modifier);
    }

    @Transactional
    public void toggleStock(UserDetails userDetails, Long modifierId) {
        User user = checkVenueOwner(userDetails);

        Modifier modifier = modifierRepository.findById(modifierId)
                .orElseThrow(() -> new RuntimeException("modifier not found"));

        checkAccess(user, modifier);

        modifier.setInStock(!modifier.getInStock());

        modifierRepository.save(modifier);
    }

    @Transactional
    public void deleteGroup(UserDetails userDetails, Long modifierGroupId) {
        User user = checkVenueOwner(userDetails);

        ModifierGroup modifierGroup = modifierGroupRepository.findById(modifierGroupId)
                .orElseThrow(() -> new RuntimeException("modifier group not found"));

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

        modifierGroup.setRequired(!modifierGroup.getRequired());

        modifierGroupRepository.save(modifierGroup);
    }

    public User checkVenueOwner(UserDetails userDetails) {
        if(!(userDetails instanceof User user) || user.getRole() != UserRole.VENUE_OWNER)
            throw new RuntimeException("not allowed");
        return user;
    }

    public void checkAccess(User user, Modifier modifier) {
        if(!modifier.getModifierGroup().getProduct().getVenue().getOwner().getId().equals(user.getId()))
            throw new RuntimeException("not allowed to change");
    }

    public void checkAccess(User user, ModifierGroup modifierGroup) {
        if(!modifierGroup.getProduct().getVenue().getOwner().getId().equals(user.getId()))
            throw new RuntimeException("not allowed to change");
    }


}
