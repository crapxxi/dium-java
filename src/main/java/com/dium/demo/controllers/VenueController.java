package com.dium.demo.controllers;

import com.dium.demo.dto.requests.VenueRequest;
import com.dium.demo.dto.responses.VenueResponse;
import com.dium.demo.services.VenueService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/venues")
public class VenueController {
    private final VenueService venueService;

    @GetMapping
    public ResponseEntity<List<VenueResponse>> getAll() {
        return ResponseEntity.ok(venueService.getAllVenues());
    }

    @GetMapping("/{id}")
    public ResponseEntity<VenueResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(venueService.getVenueById(id));
    }

    @PreAuthorize("hasRole('VENUE_OWNER')")
    @PostMapping(value = "/manage", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<VenueResponse> createVenue(
            @Valid @RequestPart("venueRequest") VenueRequest request,
            @RequestPart(value = "image", required = false) MultipartFile image
    ) throws IOException {
        return ResponseEntity.ok(venueService.createVenue(request, image));
    }

    @PreAuthorize("hasRole('VENUE_OWNER')")
    @PutMapping(value = "/manage", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<VenueResponse> updateVenue(
            @Valid @RequestPart("venueRequest") VenueRequest request,
            @RequestPart(value = "image", required = false) MultipartFile image
    ) throws IOException {
        return ResponseEntity.ok(venueService.updateVenue(request, image));
    }

    @GetMapping("/manage/my")
    public ResponseEntity<VenueResponse> getUserVenue() {
        return ResponseEntity.ok(venueService.getOwnerVenue());
    }

    @PatchMapping("/manage/toggleWork")
    public ResponseEntity<Void> toggleWork() {
        venueService.toggleWork();
        return ResponseEntity.ok().build();
    }

}
