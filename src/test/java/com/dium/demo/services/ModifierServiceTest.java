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
import com.dium.demo.models.Venue;
import com.dium.demo.repositories.ModifierGroupRepository;
import com.dium.demo.repositories.ModifierRepository;
import com.dium.demo.repositories.ProductRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ModifierServiceTest {

    @Mock
    private ProductRepository productRepository;
    @Mock
    private ModifierRepository modifierRepository;
    @Mock
    private ModifierGroupRepository modifierGroupRepository;
    @Mock
    private ModifierGroupMapper modifierGroupMapper;
    @Mock
    private ModifierMapper modifierMapper;
    @Mock
    private CustomUserDetailsService userDetailsService;

    @InjectMocks
    private ModifierService modifierService;

    private User venueOwner;
    private Venue venue;
    private Product product;
    private ModifierGroup modifierGroup;
    private Modifier modifier;

    @BeforeEach
    void setUp() {
        venueOwner = new User();
        venueOwner.setId(1L);
        venueOwner.setRole(UserRole.VENUE_OWNER);

        venue = new Venue();
        venue.setId(1L);
        venue.setOwner(venueOwner);

        product = new Product();
        product.setId(1L);
        product.setVenue(venue);

        modifierGroup = new ModifierGroup();
        modifierGroup.setId(1L);
        modifierGroup.setProduct(product);
        modifierGroup.setModifiers(new ArrayList<>());
        modifierGroup.setIsDeleted(false);
        modifierGroup.setRequired(false);

        modifier = new Modifier();
        modifier.setId(1L);
        modifier.setModifierGroup(modifierGroup);
        modifier.setPriceDelta(BigDecimal.TEN);
        modifier.setInStock(true);

        modifierGroup.getModifiers().add(modifier);
    }

    @Test
    void addModifierGroup_Success() {
        ModifierGroupRequest request = new ModifierGroupRequest("Group", true, 1, 1, new ArrayList<>());
        ModifierGroupResponse response = new ModifierGroupResponse(1L, "Group", true, 1, 1, new ArrayList<>());

        when(userDetailsService.getCurrentUser()).thenReturn(venueOwner);
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(modifierGroupMapper.toEntity(request)).thenReturn(modifierGroup);
        when(modifierGroupRepository.save(any(ModifierGroup.class))).thenReturn(modifierGroup);
        when(modifierGroupMapper.toResponse(modifierGroup)).thenReturn(response);

        ModifierGroupResponse result = modifierService.addModifierGroup(1L, request);

        assertThat(result).isNotNull();
        verify(modifierGroupRepository).save(modifierGroup);
        assertThat(modifierGroup.getProduct()).isEqualTo(product);
        assertThat(modifierGroup.getIsDeleted()).isFalse();
    }

    @Test
    void addModifierGroup_NotVenueOwner_ThrowsAccessDeniedException() {
        User client = new User();
        client.setId(2L);
        client.setRole(UserRole.CLIENT);
        when(userDetailsService.getCurrentUser()).thenReturn(client);

        assertThatThrownBy(() -> modifierService.addModifierGroup(1L, new ModifierGroupRequest("G", true, 1, 1, null)))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void addModifierGroup_WrongOwner_ThrowsAccessDeniedException() {
        User anotherOwner = new User();
        anotherOwner.setId(99L);
        anotherOwner.setRole(UserRole.VENUE_OWNER);

        when(userDetailsService.getCurrentUser()).thenReturn(anotherOwner);
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));

        assertThatThrownBy(() -> modifierService.addModifierGroup(1L, new ModifierGroupRequest("G", true, 1, 1, null)))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void editModifier_Success() {
        ModifierRequest request = new ModifierRequest("Mod", BigDecimal.ONE, true);
        ModifierResponse response = new ModifierResponse(1L, "Mod", BigDecimal.ONE, true);

        when(userDetailsService.getCurrentUser()).thenReturn(venueOwner);
        when(modifierRepository.findById(1L)).thenReturn(Optional.of(modifier));
        when(modifierRepository.save(any(Modifier.class))).thenReturn(modifier);
        when(modifierMapper.toResponse(modifier)).thenReturn(response);

        ModifierResponse result = modifierService.editModifier(1L, request);

        assertThat(result).isNotNull();
        verify(modifierMapper).updateFromRequest(request, modifier);
        verify(modifierRepository).save(modifier);
    }

    @Test
    void editModifier_NegativePriceDelta_ThrowsBusinessLogicException() {
        ModifierRequest request = new ModifierRequest("Mod", BigDecimal.valueOf(-10), true);

        when(userDetailsService.getCurrentUser()).thenReturn(venueOwner);
        when(modifierRepository.findById(1L)).thenReturn(Optional.of(modifier));

        assertThatThrownBy(() -> modifierService.editModifier(1L, request))
                .isInstanceOf(BusinessLogicException.class);
    }

    @Test
    void editModifierGroup_Success() {
        ModifierGroupRequest request = new ModifierGroupRequest("Group", true, 1, 1, new ArrayList<>());
        ModifierGroupResponse response = new ModifierGroupResponse(1L, "Group", true, 1, 1, new ArrayList<>());

        when(userDetailsService.getCurrentUser()).thenReturn(venueOwner);
        when(modifierGroupRepository.findById(1L)).thenReturn(Optional.of(modifierGroup));
        when(modifierGroupRepository.save(any(ModifierGroup.class))).thenReturn(modifierGroup);
        when(modifierGroupMapper.toResponse(modifierGroup)).thenReturn(response);

        ModifierGroupResponse result = modifierService.editModifierGroup(1L, request);

        assertThat(result).isNotNull();
        verify(modifierGroupMapper).updateFromRequest(request, modifierGroup);
        verify(modifierGroupRepository).save(modifierGroup);
        assertThat(modifierGroup.getProduct()).isEqualTo(product);
    }

    @Test
    void delete_Success() {
        when(userDetailsService.getCurrentUser()).thenReturn(venueOwner);
        when(modifierRepository.findById(1L)).thenReturn(Optional.of(modifier));

        modifierService.delete(1L);

        verify(modifierRepository).delete(modifier);
    }

    @Test
    void deleteModifierGroup_Success() {
        when(userDetailsService.getCurrentUser()).thenReturn(venueOwner);
        when(modifierGroupRepository.findById(1L)).thenReturn(Optional.of(modifierGroup));

        modifierService.deleteModifierGroup(1L);

        verify(modifierGroupRepository).save(modifierGroup);
        assertThat(modifierGroup.getIsDeleted()).isTrue();
    }

    @Test
    void toggleRequired_Success() {
        when(userDetailsService.getCurrentUser()).thenReturn(venueOwner);
        when(modifierGroupRepository.findById(1L)).thenReturn(Optional.of(modifierGroup));

        modifierService.toggleRequired(1L);

        verify(modifierGroupRepository).save(modifierGroup);
        assertThat(modifierGroup.getRequired()).isTrue();
    }

    @Test
    void toggleStock_Success() {
        when(userDetailsService.getCurrentUser()).thenReturn(venueOwner);
        when(modifierRepository.findById(1L)).thenReturn(Optional.of(modifier));

        modifierService.toggleStock(1L);

        verify(modifierRepository).save(modifier);
        assertThat(modifier.getInStock()).isFalse();
    }

    @Test
    void getModifierGroups_Success() {
        List<ModifierGroup> groups = List.of(modifierGroup);
        List<ModifierGroupResponse> responses = List.of(new ModifierGroupResponse(1L, "G", true, 1, 1, null));

        when(modifierGroupRepository.findAllByProductIdAndIsDeletedFalse(1L)).thenReturn(groups);
        when(modifierGroupMapper.toResponseList(groups)).thenReturn(responses);

        List<ModifierGroupResponse> result = modifierService.getModifierGroups(1L);

        assertThat(result).hasSize(1);
        verify(modifierGroupRepository).findAllByProductIdAndIsDeletedFalse(1L);
    }

    @Test
    void entityNotFound_ThrowsException() {
        when(userDetailsService.getCurrentUser()).thenReturn(venueOwner);
        when(productRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> modifierService.addModifierGroup(1L, new ModifierGroupRequest("G", true, 1, 1, null)))
                .isInstanceOf(EntityNotFoundException.class);
    }
}