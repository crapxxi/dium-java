package com.dium.demo.services;

import com.dium.demo.dto.requests.VenueRequest;
import com.dium.demo.dto.responses.VenueResponse;
import com.dium.demo.enums.UserRole;
import com.dium.demo.exceptions.AccessDeniedException;
import com.dium.demo.exceptions.BusinessLogicException;
import com.dium.demo.mappers.VenueMapper;
import com.dium.demo.models.User;
import com.dium.demo.models.Venue;
import com.dium.demo.repositories.VenueRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VenueServiceTest {

    @Mock
    private VenueRepository venueRepository;
    @Mock
    private VenueMapper venueMapper;
    @Mock
    private FileService fileService;
    @Mock
    private CustomUserDetailsService userDetailsService;

    @InjectMocks
    private VenueService venueService;

    private User venueOwner;
    private Venue venue;
    private VenueRequest validRequest;
    private VenueResponse dummyResponse;
    private MultipartFile imageFile;

    @BeforeEach
    void setUp() {
        venueOwner = new User();
        venueOwner.setId(1L);
        venueOwner.setRole(UserRole.VENUE_OWNER);

        venue = new Venue();
        venue.setId(1L);
        venue.setOwner(venueOwner);
        venue.setIsWorking(true);
        venue.setImageUrl("old_image.jpg");

        validRequest = new VenueRequest(
                "Test Venue",
                "Description",
                "Address 123",
                null,
                true,
                BigDecimal.valueOf(500)
        );

        dummyResponse = new VenueResponse(
                1L, "Test Venue", "Description", "Address 123", "old_image.jpg", true, "testKaspiUrl" , BigDecimal.valueOf(500), true
        );

        imageFile = new MockMultipartFile("image", "test.jpg", "image/jpeg", "data".getBytes());
    }

    @Test
    void getAllVenues_Success() {
        when(venueRepository.findAll()).thenReturn(List.of(venue));
        when(venueMapper.toResponseList(anyList())).thenReturn(List.of(dummyResponse));

        List<VenueResponse> result = venueService.getAllVenues();

        assertThat(result).hasSize(1);
        verify(venueRepository).findAll();
    }

    @Test
    void getVenueById_Success() {
        when(venueRepository.findByIdOrThrow(1L)).thenReturn(venue);
        when(venueMapper.toResponse(venue)).thenReturn(dummyResponse);

        VenueResponse result = venueService.getVenueById(1L);

        assertThat(result).isNotNull();
        verify(venueRepository).findByIdOrThrow(1L);
    }

    @Test
    void getOwnerVenue_Success() {
        when(userDetailsService.getCurrentUser()).thenReturn(venueOwner);
        when(venueRepository.findByOwner_Id(1L)).thenReturn(Optional.of(venue));
        when(venueMapper.toResponse(venue)).thenReturn(dummyResponse);

        VenueResponse result = venueService.getOwnerVenue();

        assertThat(result).isNotNull();
        verify(venueRepository).findByOwner_Id(1L);
    }

    @Test
    void getOwnerVenue_NotOwner_ThrowsException() {
        User client = new User();
        client.setId(2L);
        client.setRole(UserRole.CLIENT);
        when(userDetailsService.getCurrentUser()).thenReturn(client);

        assertThatThrownBy(() -> venueService.getOwnerVenue())
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void updateVenue_Success_WithImage() throws IOException {
        when(userDetailsService.getCurrentUser()).thenReturn(venueOwner);
        when(venueRepository.findByOwner_Id(1L)).thenReturn(Optional.of(venue));
        when(fileService.saveFile(imageFile)).thenReturn("new_image.jpg");
        when(venueRepository.save(any(Venue.class))).thenReturn(venue);
        when(venueMapper.toResponse(any(Venue.class))).thenReturn(dummyResponse);

        VenueResponse result = venueService.updateVenue(validRequest, imageFile);

        assertThat(result).isNotNull();
        verify(fileService).deleteFile("old_image.jpg");
        verify(venueMapper).updateVenueFromRequest(validRequest, venue);
        assertThat(venue.getImageUrl()).isEqualTo("new_image.jpg");
        assertThat(venue.getDeliveryPrice()).isEqualTo(BigDecimal.valueOf(500));
    }

    @Test
    void updateVenue_Success_WithoutImage() throws IOException {
        when(userDetailsService.getCurrentUser()).thenReturn(venueOwner);
        when(venueRepository.findByOwner_Id(1L)).thenReturn(Optional.of(venue));
        when(venueRepository.save(any(Venue.class))).thenReturn(venue);
        when(venueMapper.toResponse(any(Venue.class))).thenReturn(dummyResponse);

        VenueResponse result = venueService.updateVenue(validRequest, null);

        assertThat(result).isNotNull();
        verify(fileService, never()).deleteFile(anyString());
        verify(fileService, never()).saveFile(any());
        verify(venueMapper).updateVenueFromRequest(validRequest, venue);
        assertThat(venue.getDeliveryPrice()).isEqualTo(BigDecimal.valueOf(500));
    }

    @Test
    void updateVenue_NegativeDeliveryPrice_ThrowsException() {
        VenueRequest invalidRequest = new VenueRequest("Name", "Desc", "Addr", null, true, BigDecimal.valueOf(-10));
        when(userDetailsService.getCurrentUser()).thenReturn(venueOwner);
        when(venueRepository.findByOwner_Id(1L)).thenReturn(Optional.of(venue));

        assertThatThrownBy(() -> venueService.updateVenue(invalidRequest, null))
                .isInstanceOf(BusinessLogicException.class);
    }

    @Test
    void createVenue_Success_WithImage() throws IOException {
        when(userDetailsService.getCurrentUser()).thenReturn(venueOwner);
        when(venueRepository.existsByOwner_Id(1L)).thenReturn(false);
        when(venueMapper.toEntity(validRequest)).thenReturn(new Venue());
        when(fileService.saveFile(imageFile)).thenReturn("path.jpg");
        when(venueRepository.save(any(Venue.class))).thenReturn(venue);
        when(venueMapper.toResponse(any(Venue.class))).thenReturn(dummyResponse);

        VenueResponse result = venueService.createVenue(validRequest, imageFile);

        assertThat(result).isNotNull();
        ArgumentCaptor<Venue> venueCaptor = ArgumentCaptor.forClass(Venue.class);
        verify(venueRepository).save(venueCaptor.capture());

        Venue savedVenue = venueCaptor.getValue();
        assertThat(savedVenue.getOwner()).isEqualTo(venueOwner);
        assertThat(savedVenue.getImageUrl()).isEqualTo("path.jpg");
    }

    @Test
    void createVenue_AlreadyExists_ThrowsException() {
        when(userDetailsService.getCurrentUser()).thenReturn(venueOwner);
        when(venueRepository.existsByOwner_Id(1L)).thenReturn(true);

        assertThatThrownBy(() -> venueService.createVenue(validRequest, null))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("User already has a venue");
    }

    @Test
    void toggleWork_Success() {
        when(userDetailsService.getCurrentUser()).thenReturn(venueOwner);
        when(venueRepository.findByOwner_Id(1L)).thenReturn(Optional.of(venue));

        venueService.toggleWork();

        assertThat(venue.getIsWorking()).isFalse();
    }
}