package com.dium.demo.services;

import com.dium.demo.dto.venue_product.ProductDTO;
import com.dium.demo.enums.UserRole;
import com.dium.demo.mappers.ProductMapper;
import com.dium.demo.models.Product;
import com.dium.demo.models.User;
import com.dium.demo.models.Venue;
import com.dium.demo.repositories.ProductRepository;
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
public class ProductService {
    private final ProductRepository productRepository;
    private final ProductMapper productMapper;
    private final VenueRepository venueRepository;
    private final FileService fileService;

    @Transactional(readOnly = true)
    public List<ProductDTO> getMenuByVenueId(Long venueId) {
        List<Product> products = productRepository.findAllByVenueIdAndDeletedFalse(venueId);
        return productMapper.toDtoList(products);
    }

    @Transactional
    public ProductDTO addProduct(UserDetails userDetails, ProductDTO productDto, MultipartFile image) throws IOException {
        if (!(userDetails instanceof User user) || user.getRole() != UserRole.VENUE_OWNER) {
            throw new RuntimeException("Only venue owners can add products");
        }

        Venue venue = venueRepository.findByOwnerId(user.getId())
                .orElseThrow(() -> new RuntimeException("Venue not found"));

        String imageUrl = fileService.saveFile(image);

        Product product = productMapper.toEntity(productDto);
        product.setVenue(venue);
        product.setImageUrl(imageUrl);
        product.setInStock(false);
        product.setDeleted(false);
        product.setHasModifiers(productDto.hasModifiers());

        Product saved = productRepository.save(product);
        return productMapper.toDto(saved);
    }

    @Transactional
    public ProductDTO updateProduct(UserDetails userDetails, Long productId, ProductDTO dto, MultipartFile image) throws IOException {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new RuntimeException("Product not found"));

        User currentUser = (User) userDetails;
        if (!Objects.equals(product.getVenue().getOwner().getId(), currentUser.getId())) {
            throw new RuntimeException("You are not the owner of the venue this product belongs to!");
        }

        if (image != null && !image.isEmpty()) {
            if (product.getImageUrl() != null) {
                fileService.deleteFile(product.getImageUrl());
            }

            String newImagePath = fileService.saveFile(image);
            product.setImageUrl(newImagePath);
        }

        productMapper.updateProductFromDto(dto, product);

        Product updatedProduct = productRepository.save(product);
        return productMapper.toDto(updatedProduct);
    }

    @Transactional
    public void deleteProduct(UserDetails userDetails, Long productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new RuntimeException("Product not found"));

        User currentUser = (User) userDetails;
        if (!Objects.equals(product.getVenue().getOwner().getId(), currentUser.getId())) {
            throw new RuntimeException("You do not have permission to delete this product!");
        }

        if (product.getImageUrl() != null) {
            fileService.deleteFile(product.getImageUrl());
        }

        product.setDeleted(true);
        product.setInStock(false);
        product.setImageUrl("");

        productRepository.save(product);
    }
    @Transactional
    public void toggleStock(UserDetails userDetails, Long productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new RuntimeException("Product not found"));
        User currentUser = (User) userDetails;
        if(!Objects.equals(product.getVenue().getOwner().getId(), currentUser.getId()))
            throw new RuntimeException("You don't have permission to edit this product!");

        product.setInStock(!product.getInStock());

        productRepository.save(product);
    }
}
