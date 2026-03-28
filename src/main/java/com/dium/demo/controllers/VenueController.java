package com.dium.demo.controllers;

import com.dium.demo.dto.venue_product.VenueDTO;
import com.dium.demo.services.VenueService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Encoding;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/venues")
@Tag(name = "venues")
public class VenueController {
    private final VenueService venueService;

    @GetMapping
    @Operation(summary = "getAll")
    public ResponseEntity<?> getAll() {
        return ResponseEntity.ok(venueService.getAllVenues());
    }

    @GetMapping("/{id}")
    @Operation(summary = "get by id")
    public ResponseEntity<?> getById(@PathVariable Long id) {
        return ResponseEntity.ok(venueService.getVenueById(id));
    }

    @PostMapping(value = "/manage", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
            summary = "create Venue",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    content = @Content(
                            encoding = @Encoding(name = "venue", contentType = "application/json")
                    )
            )
    )
    public ResponseEntity<VenueDTO> createVenue(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestPart("venue") VenueDTO venueDTO,
            @RequestPart(value = "image", required = false) MultipartFile image
    ) throws IOException {
        return ResponseEntity.ok(venueService.createVenue(userDetails, venueDTO, image));
    }

    @PutMapping(value = "/manage", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
            summary = "update Venue",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    content = @Content(
                            encoding = @Encoding(name = "venue", contentType = "application/json")
                    )
            )
    )
    public ResponseEntity<VenueDTO> updateVenue(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestPart("venue") VenueDTO venueDTO,
            @RequestPart(value = "image", required = false) MultipartFile image
    ) throws IOException {
        return ResponseEntity.ok(venueService.updateVenue(userDetails, venueDTO, image));
    }

    @GetMapping("/manage/my")
    @Operation(summary = "get user's venue")
    public ResponseEntity<?> getUserVenue(@AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(venueService.getOwnerVenue(userDetails));
    }

}
