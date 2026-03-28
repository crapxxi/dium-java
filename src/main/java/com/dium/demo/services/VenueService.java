package com.dium.demo.services;

import com.dium.demo.dto.venue_product.VenueDTO;
import com.dium.demo.enums.UserRole;
import com.dium.demo.mappers.VenueMapper;
import com.dium.demo.models.User;
import com.dium.demo.models.Venue;
import com.dium.demo.repositories.VenueRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class VenueService {
    private final VenueRepository venueRepository;
    private final VenueMapper venueMapper;
    private final FileService fileService;

    @Transactional(readOnly = true)
    public List<VenueDTO> getAllVenues() {
        return venueMapper.toDtoList(venueRepository.findAll());
    }
    @Transactional(readOnly = true)
    public VenueDTO getVenueById(Long venueId) {
        return venueMapper.toDto(venueRepository.findById(venueId)
                .orElseThrow(() -> new RuntimeException("Venue is not found!")));
    }

    @Transactional(readOnly = true)
    public VenueDTO getOwnerVenue(UserDetails userDetails) {
        if(!(userDetails instanceof User user) || user.getRole() != UserRole.VENUE_OWNER)
            throw new RuntimeException("Only a venue owner can get own venues");

        return venueMapper.toDto(venueRepository.findByOwnerId(user.getId())
                .orElseThrow(() -> new RuntimeException("Venue not found")));
    }

    @Transactional
    public VenueDTO updateVenue(UserDetails userDetails, VenueDTO venueDTO, MultipartFile image) throws IOException {
        if(!(userDetails instanceof User user) || user.getRole() != UserRole.VENUE_OWNER)
            throw new RuntimeException("Only a venue owner can update venue");

        Venue venue = venueRepository.findByOwnerId(user.getId())
                .orElseThrow(() -> new RuntimeException("Venue not found"));

        if (!Objects.equals(venue.getOwner().getId(), user.getId()))
            throw new RuntimeException("You are not the owner of this venue");

        venueMapper.updateVenueFromDto(venueDTO, venue);
        if (image != null && !image.isEmpty()) {
            fileService.deleteFile(venue.getImageUrl());
            String newPath = fileService.saveFile(image);
            venue.setImageUrl(newPath);
        }

        return venueMapper.toDto(venueRepository.save(venue));
    }

    @Transactional
    public VenueDTO createVenue(UserDetails userDetails, VenueDTO venueDTO, MultipartFile image) throws IOException {
        if (!(userDetails instanceof User user) || user.getRole() != UserRole.VENUE_OWNER)
            throw new RuntimeException("Only a venue owner can create a venue");

        Venue venue = venueMapper.toEntity(venueDTO);
        venue.setOwner(user);
        if (image != null && !image.isEmpty()) {
            String path = fileService.saveFile(image);
            venue.setImageUrl(path);
        }

        return venueMapper.toDto(venueRepository.save(venue));
    }
}
