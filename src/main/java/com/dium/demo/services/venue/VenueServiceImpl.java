package com.dium.demo.services.venue;

import com.dium.demo.dto.requests.VenueRequest;
import com.dium.demo.dto.responses.VenueResponse;
import com.dium.demo.enums.UserRole;
import com.dium.demo.exceptions.BusinessLogicException;
import com.dium.demo.mappers.VenueMapper;
import com.dium.demo.models.User;
import com.dium.demo.models.Venue;
import com.dium.demo.repositories.VenueRepository;
import com.dium.demo.security.access.RequireRole;
import com.dium.demo.services.auth.CustomUserDetailsService;
import com.dium.demo.services.file.FileService;
import com.dium.demo.utils.MoneyValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class VenueServiceImpl implements VenueService {
    private static final String NEGATIVE_DELIVERY_PRICE = "Delivery price can't be negative";

    private final VenueRepository venueRepository;
    private final VenueMapper venueMapper;
    private final FileService fileService;
    private final CustomUserDetailsService userDetailsService;
    private final CurrentVenueProvider currentVenueProvider;

    @Transactional(readOnly = true)
    @Override
    public List<VenueResponse> getAllVenues() {
        return venueMapper.toResponseList(venueRepository.findAll());
    }

    @Transactional(readOnly = true)
    @Override
    public VenueResponse getVenueById(Long venueId) {
        return venueMapper.toResponse(venueRepository.findByIdOrThrow(venueId));
    }

    @RequireRole(UserRole.VENUE_OWNER)
    @Transactional(readOnly = true)
    @Override
    public VenueResponse getOwnerVenue() {
        return venueMapper.toResponse(currentVenueProvider.getCurrentVenue());
    }

    @RequireRole(UserRole.VENUE_OWNER)
    @Transactional
    @Override
    public VenueResponse createVenue(VenueRequest request, MultipartFile image) throws IOException {
        User owner = userDetailsService.getCurrentUser();

        if (venueRepository.existsByOwner_Id(owner.getId())) {
            throw new BusinessLogicException("User already has a venue");
        }

        MoneyValidator.requireNonNegativeIfPresent(request.deliveryPrice(), NEGATIVE_DELIVERY_PRICE);

        Venue venue = venueMapper.toEntity(request);
        venue.setOwner(owner);
        venue.setImageUrl(fileService.replaceFile(null, image));
        venue.setDeliveryPrice(request.deliveryPrice() != null ? request.deliveryPrice() : BigDecimal.ZERO);

        return venueMapper.toResponse(venueRepository.save(venue));
    }

    @RequireRole(UserRole.VENUE_OWNER)
    @Transactional
    @Override
    public VenueResponse updateVenue(VenueRequest request, MultipartFile image) throws IOException {
        Venue venue = currentVenueProvider.getCurrentVenue();

        MoneyValidator.requireNonNegative(request.deliveryPrice(), NEGATIVE_DELIVERY_PRICE);

        venueMapper.updateVenueFromRequest(request, venue);
        venue.setImageUrl(fileService.replaceFile(venue.getImageUrl(), image));
        venue.setDeliveryPrice(request.deliveryPrice());

        return venueMapper.toResponse(venueRepository.save(venue));
    }

    @RequireRole(UserRole.VENUE_OWNER)
    @Transactional
    @Override
    public void toggleWork() {
        Venue venue = currentVenueProvider.getCurrentVenue();
        venue.setIsWorking(!Boolean.TRUE.equals(venue.getIsWorking()));
    }
}
