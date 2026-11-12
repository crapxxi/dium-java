package com.dium.demo.services.product.impl;

import com.dium.demo.dto.requests.ProductRequest;
import com.dium.demo.dto.responses.ProductResponse;
import com.dium.demo.enums.UserRole;
import com.dium.demo.mappers.ProductMapper;
import com.dium.demo.models.Product;
import com.dium.demo.repositories.ProductRepository;
import com.dium.demo.security.access.CheckOwnership;
import com.dium.demo.security.access.OwnedResource;
import com.dium.demo.security.access.RequireRole;
import com.dium.demo.services.file.FileService;
import com.dium.demo.services.product.ProductService;
import com.dium.demo.services.venue.CurrentVenueProvider;
import com.dium.demo.utils.MoneyValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ProductServiceImpl implements ProductService {
    private static final String NEGATIVE_PRICE = "Price can't be negative";

    private final ProductRepository productRepository;
    private final ProductMapper productMapper;
    private final FileService fileService;
    private final CurrentVenueProvider currentVenueProvider;

    @Transactional(readOnly = true)
    @Override
    public List<ProductResponse> getMenuByVenueId(Long venueId) {
        return productMapper.toResponseList(productRepository.findAllByVenueIdAndDeletedFalse(venueId));
    }

    @RequireRole(UserRole.VENUE_OWNER)
    @Transactional
    @Override
    public ProductResponse addProduct(ProductRequest request, MultipartFile image) throws IOException {
        MoneyValidator.requireNonNegative(request.price(), NEGATIVE_PRICE);

        Product product = productMapper.toEntity(request);
        product.setVenue(currentVenueProvider.getCurrentVenue());
        if (image != null && !image.isEmpty()) {
            product.setImageUrl(fileService.saveFile(image));
        }
        product.setInStock(false);
        product.setDeleted(false);

        return productMapper.toResponse(productRepository.save(product));
    }

    @RequireRole(UserRole.VENUE_OWNER)
    @CheckOwnership(OwnedResource.PRODUCT)
    @Transactional
    @Override
    public ProductResponse updateProduct(Long productId, ProductRequest request, MultipartFile image) throws IOException {
        MoneyValidator.requireNonNegative(request.price(), NEGATIVE_PRICE);

        Product product = productRepository.findByIdOrThrow(productId);
        product.setImageUrl(fileService.replaceFile(product.getImageUrl(), image));
        productMapper.updateProductFromRequest(request, product);

        return productMapper.toResponse(productRepository.save(product));
    }

    @RequireRole(UserRole.VENUE_OWNER)
    @CheckOwnership(OwnedResource.PRODUCT)
    @Transactional
    @Override
    public void deleteProduct(Long productId) {
        Product product = productRepository.findByIdOrThrow(productId);

        if (product.getImageUrl() != null) {
            fileService.deleteFile(product.getImageUrl());
        }

        product.setDeleted(true);
        product.setInStock(false);
        product.setImageUrl(null);
    }

    @RequireRole(UserRole.VENUE_OWNER)
    @CheckOwnership(OwnedResource.PRODUCT)
    @Transactional
    @Override
    public void toggleStock(Long productId) {
        Product product = productRepository.findByIdOrThrow(productId);
        product.setInStock(!Boolean.TRUE.equals(product.getInStock()));
    }
}
