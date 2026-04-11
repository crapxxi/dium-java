package com.dium.demo.services;

import com.dium.demo.dto.requests.ProductRequest;
import com.dium.demo.dto.responses.ProductResponse;
import com.dium.demo.enums.UserRole;
import com.dium.demo.exceptions.AccessDeniedException;
import com.dium.demo.exceptions.BusinessLogicException;
import com.dium.demo.mappers.ProductMapper;
import com.dium.demo.models.Product;
import com.dium.demo.models.User;
import com.dium.demo.models.Venue;
import com.dium.demo.repositories.ProductRepository;
import com.dium.demo.repositories.VenueRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class ProductService {
    private final ProductRepository productRepository;
    private final ProductMapper productMapper;
    private final VenueRepository venueRepository;
    private final FileService fileService;
    private final CustomUserDetailsService userDetailsService;

    @Transactional(readOnly = true)
    public List<ProductResponse> getMenuByVenueId(Long venueId) {
        List<Product> products = productRepository.findAllByVenueIdAndDeletedFalse(venueId);
        return productMapper.toResponseList(products);
    }

    @Transactional
    public ProductResponse addProduct(ProductRequest request, MultipartFile image) throws IOException {
       User user = userDetailsService.getCurrentUser();

       checkVenueOwner(user);

       checkPriceForNegative(request.price());

       Venue venue = venueRepository.findByOwner_Id(user.getId())
               .orElseThrow(() -> new EntityNotFoundException("Venue not found"));

       Product product = productMapper.toEntity(request);
       product.setVenue(venue);
        if(image != null && !image.isEmpty()) {
            String imageUrl = fileService.saveFile(image);
            product.setImageUrl(imageUrl);
        }
       product.setInStock(false);
       product.setDeleted(false);
       product.setHasModifiers(request.hasModifiers());

       Product saved = productRepository.save(product);
       return productMapper.toResponse(saved);
    }

    @Transactional
    public ProductResponse updateProduct(Long productId, ProductRequest request, MultipartFile image) throws IOException {

        User user = userDetailsService.getCurrentUser();

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new EntityNotFoundException("Product not found"));


        checkVenueOwner(user);

        checkVenueHasThisProduct(user, product);

        checkPriceForNegative(request.price());

        if (image != null && !image.isEmpty()) {
            if (product.getImageUrl() != null && !product.getImageUrl().isBlank()) {
                fileService.deleteFile(product.getImageUrl());
            }
            String newImagePath = fileService.saveFile(image);
            product.setImageUrl(newImagePath);
        }

        Venue currentVenue = product.getVenue();
        productMapper.updateProductFromRequest(request, product);
        product.setVenue(currentVenue);

        return productMapper.toResponse(productRepository.save(product));
    }

    @Transactional
    public void deleteProduct(Long productId) {
        User user = userDetailsService.getCurrentUser();
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new EntityNotFoundException("Product not found"));

        checkVenueOwner(user);

        checkVenueHasThisProduct(user, product);

        if (product.getImageUrl() != null) {
            fileService.deleteFile(product.getImageUrl());
        }

        product.setDeleted(true);
        product.setInStock(false);
        product.setImageUrl(null);

        productRepository.save(product);
    }
    @Transactional
    public void toggleStock(Long productId) {
        User user = userDetailsService.getCurrentUser();

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new EntityNotFoundException("Product not found"));

        checkVenueOwner(user);
        checkVenueHasThisProduct(user, product);

        product.setInStock(!product.getInStock());

        productRepository.save(product);
    }

    private void checkVenueOwner(User user) {
        if (!user.getRole().equals(UserRole.VENUE_OWNER))
            throw new AccessDeniedException("Access denied: not venue owner");

    }

    private void checkPriceForNegative(BigDecimal price) {
        if(price == null || price.compareTo(BigDecimal.ZERO) < 0)
            throw new BusinessLogicException("Price can't be negative");
    }

    private void checkVenueHasThisProduct(User user, Product product) {
        if(!product.getVenue().getOwner().getId().equals(user.getId()))
            throw new AccessDeniedException("Access denied: can't change product, wrong venue owner");
    }

}
