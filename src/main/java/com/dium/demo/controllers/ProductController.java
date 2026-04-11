package com.dium.demo.controllers;

import com.dium.demo.dto.requests.ModifierGroupRequest;
import com.dium.demo.dto.requests.ProductRequest;
import com.dium.demo.dto.responses.ModifierGroupResponse;
import com.dium.demo.dto.responses.ProductResponse;
import com.dium.demo.services.ModifierService;
import com.dium.demo.services.ProductService;
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
@RequestMapping("/api/v1/products")
public class ProductController {
    private final ProductService productService;
    private final ModifierService modifierService;

    @GetMapping("/venue/{venueId}")
    public ResponseEntity<List<ProductResponse>> getByVenue(@PathVariable Long venueId) {
        return ResponseEntity.ok(productService.getMenuByVenueId(venueId));
    }

    @PreAuthorize("hasRole('VENUE_OWNER')")
    @PostMapping(value = "/venue/products", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ProductResponse> addProduct(
            @Valid @RequestPart("productRequest") ProductRequest request,
            @RequestPart("image") MultipartFile image
    ) throws IOException {
        return ResponseEntity.ok(productService.addProduct(request, image));
    }

    @PreAuthorize("hasRole('VENUE_OWNER')")
    @PutMapping(value = "/{productId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ProductResponse> updateProduct(
            @PathVariable Long productId,
            @Valid @RequestPart("productRequest") ProductRequest request,
            @RequestPart(value = "image", required = false) MultipartFile image
    ) throws IOException {
        return ResponseEntity.ok(productService.updateProduct(productId, request, image));
    }

    @PreAuthorize("hasRole('VENUE_OWNER')")
    @DeleteMapping("/{productId}")
    public ResponseEntity<Void> deleteProduct(
            @PathVariable Long productId
    ) {
        productService.deleteProduct(productId);
        return ResponseEntity.ok().build();
    }
    @PreAuthorize("hasRole('VENUE_OWNER')")
    @PatchMapping("/{productId}")
    public ResponseEntity<Void> toggleStock(@PathVariable Long productId) {
        productService.toggleStock(productId);
        return ResponseEntity.ok().build();
    }

    @PreAuthorize("hasRole('VENUE_OWNER')")
    @PostMapping("/{productId}/modifier-groups")
    public ResponseEntity<ModifierGroupResponse> addModifierGroup(@PathVariable Long productId,
                                                                  @Valid @RequestBody ModifierGroupRequest request) {
        return ResponseEntity.ok(modifierService.addModifierGroup(productId, request));
    }

    @GetMapping(value = "/{productId}/modifier-groups")
    public ResponseEntity<List<ModifierGroupResponse>> getModifierGroups(@PathVariable Long productId) {
        return ResponseEntity.ok(modifierService.getModifierGroups(productId));
    }
}
