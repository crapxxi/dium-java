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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class VenueService {
    private final VenueRepository venueRepository;
    private final VenueMapper venueMapper;
    private final FileService fileService;
    private final CustomUserDetailsService userDetailsService;

    @Transactional(readOnly = true)
    public List<VenueResponse> getAllVenues() {
        return venueMapper.toResponseList(venueRepository.findAll());
    }
    @Transactional(readOnly = true)
    public VenueResponse getVenueById(Long venueId) {
        return venueMapper.toResponse(venueRepository.findByIdOrThrow(venueId));
    }

    @Transactional(readOnly = true)
    public VenueResponse getOwnerVenue() {
        User user = userDetailsService.getCurrentUser();

        checkVenueOwner(user);

        return venueMapper.toResponse(venueRepository.findByOwner_Id(user.getId())
                .orElseThrow(() -> new EntityNotFoundException("Venue not found")));
    }

    @Transactional
    public VenueResponse updateVenue(VenueRequest request, MultipartFile image) throws IOException {
        User user = userDetailsService.getCurrentUser();

        checkVenueOwner(user);

        Venue venue = venueRepository.findByOwner_Id(user.getId())
                .orElseThrow(() -> new EntityNotFoundException("Venue not found"));


        checkDeliveryPriceForNegative(request.deliveryPrice());
        venueMapper.updateVenueFromRequest(request, venue);

        if (image != null && !image.isEmpty()) {
            fileService.deleteFile(venue.getImageUrl());
            String newPath = fileService.saveFile(image);
            venue.setImageUrl(newPath);
        }

        venue.setDeliveryPrice(request.deliveryPrice());

        return venueMapper.toResponse(venueRepository.save(venue));
    }

    @Transactional
    public VenueResponse createVenue(VenueRequest request, MultipartFile image) throws IOException {
        User user = userDetailsService.getCurrentUser();
        checkVenueOwner(user);

        if (venueRepository.existsByOwner_Id(user.getId()))
            throw new RuntimeException("User already has a venue");

        checkDeliveryPriceForNegative(request.deliveryPrice());

        Venue venue = venueMapper.toEntity(request);
        venue.setOwner(user);
        if (image != null && !image.isEmpty()) {
            String path = fileService.saveFile(image);
            venue.setImageUrl(path);
        }

        return venueMapper.toResponse(venueRepository.save(venue));
    }

    @Transactional
    public void toggleWork() {
        User user = userDetailsService.getCurrentUser();
        checkVenueOwner(user);

        Venue venue = venueRepository.findByOwner_Id(user.getId())
                .orElseThrow(() -> new EntityNotFoundException("User doesn't have a venue"));

        boolean currentStatus = (venue.getIsWorking() != null) && venue.getIsWorking();
        venue.setIsWorking(!currentStatus);
    }

    private void checkVenueOwner(User user) {
        if(!user.getRole().equals(UserRole.VENUE_OWNER))
            throw new AccessDeniedException("Access denied: not venue owner");
    }

    private void checkDeliveryPriceForNegative(BigDecimal deliveryPrice) {
        if(deliveryPrice == null || deliveryPrice.compareTo(BigDecimal.ZERO) < 0)
            throw new BusinessLogicException("Delivery price can't be negative");
    }

}
