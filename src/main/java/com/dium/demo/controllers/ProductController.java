package com.dium.demo.controllers;

import com.dium.demo.dto.product_modifier.ModifierGroupDTO;
import com.dium.demo.dto.venue_product.ProductDTO;
import com.dium.demo.models.User;
import com.dium.demo.services.ModifierService;
import com.dium.demo.services.ProductService;
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
@RequestMapping("/api/v1/products")
@Tag(name = "products")
public class ProductController {
    private final ProductService productService;
    private final ModifierService modifierService;

    @GetMapping("/venue/{venueId}")
    @Operation(summary = "get products by venue")
    public ResponseEntity<?> getByVenue(@PathVariable Long venueId) {
        return ResponseEntity.ok(productService.getMenuByVenueId(venueId));
    }

    @PostMapping(value = "/venue/products", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
            summary = "create product",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    content = @Content(
                            encoding = @Encoding(name = "product", contentType = "application/json")
                    )
            )
    )
    public ResponseEntity<?> addProduct(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestPart("product") ProductDTO productDto,
            @RequestPart("image") MultipartFile image
    ) throws IOException {
        return ResponseEntity.ok(productService.addProduct(userDetails, productDto, image));
    }

    @PutMapping(value = "/{productId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
            summary = "update product",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    content = @Content(
                            encoding = @Encoding(name = "product", contentType = "application/json")
                    )
            )
    )
    public ResponseEntity<ProductDTO> updateProduct(
            @AuthenticationPrincipal User user,
            @PathVariable Long productId,
            @RequestPart("product") ProductDTO dto,
            @RequestPart(value = "image", required = false) MultipartFile image
    ) throws IOException {
        return ResponseEntity.ok(productService.updateProduct(user, productId, dto, image));
    }

    @DeleteMapping("/{productId}")
    @Operation(summary = "delete product")
    public ResponseEntity<Void> deleteProduct(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long productId
    ) {
        productService.deleteProduct(userDetails, productId);
        return ResponseEntity.ok().build();
    }

    @PatchMapping("/{productId}")
    @Operation(summary = "in stock, out stock")
    public ResponseEntity<?> toggleStock(@AuthenticationPrincipal UserDetails userDetails, @PathVariable Long productId) {
        productService.toggleStock(userDetails,productId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{productId}/modifier-groups")
    @Operation(summary = "add the modifier groups")
    public ResponseEntity<?> addModifierGroup(@AuthenticationPrincipal UserDetails userDetails,
                                              @PathVariable Long productId,
                                              @RequestBody ModifierGroupDTO modifierGroupDTO) {
        return ResponseEntity.ok(modifierService.addModifierGroup(userDetails, productId, modifierGroupDTO));
    }

    @GetMapping(value = "/{productId}/modifier-groups")
    @Operation(summary = "get the modifier groups")
    public ResponseEntity<?> getModifierGroups(@PathVariable Long productId) {
        return ResponseEntity.ok(modifierService.getModifierGroups(productId));
    }
}
