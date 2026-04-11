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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;
    @Mock
    private ProductMapper productMapper;
    @Mock
    private VenueRepository venueRepository;
    @Mock
    private FileService fileService;
    @Mock
    private CustomUserDetailsService userDetailsService;

    @InjectMocks
    private ProductService productService;

    private User venueOwner;
    private Venue venue;
    private Product product;
    private ProductRequest validRequest;
    private MultipartFile imageFile;

    @BeforeEach
    void setUp() {
        venueOwner = new User();
        venueOwner.setId(1L);
        venueOwner.setRole(UserRole.VENUE_OWNER);

        venue = new Venue();
        venue.setId(1L);
        venue.setOwner(venueOwner);

        product = new Product();
        product.setId(1L);
        product.setVenue(venue);
        product.setPrice(BigDecimal.valueOf(1000));
        product.setInStock(true);
        product.setDeleted(false);
        product.setImageUrl("old_image_url.jpg");

        validRequest = new ProductRequest(
                "Test Product",
                BigDecimal.valueOf(1500),
                null,
                "Description",
                true,
                null,
                true
        );

        imageFile = new MockMultipartFile("image", "test.jpg", "image/jpeg", "image data".getBytes());
    }

    @Test
    void getMenuByVenueId_Success() {
        when(productRepository.findAllByVenueIdAndDeletedFalse(1L)).thenReturn(List.of(product));
        when(productMapper.toResponseList(anyList())).thenReturn(List.of(mock(ProductResponse.class)));

        List<ProductResponse> result = productService.getMenuByVenueId(1L);

        assertThat(result).hasSize(1);
        verify(productRepository).findAllByVenueIdAndDeletedFalse(1L);
    }

    @Test
    void addProduct_Success_WithImage() throws IOException {
        when(userDetailsService.getCurrentUser()).thenReturn(venueOwner);
        when(venueRepository.findByOwner_Id(1L)).thenReturn(Optional.of(venue));
        when(productMapper.toEntity(validRequest)).thenReturn(new Product());
        when(fileService.saveFile(imageFile)).thenReturn("new_image_url.jpg");
        when(productRepository.save(any(Product.class))).thenReturn(product);
        when(productMapper.toResponse(any(Product.class))).thenReturn(mock(ProductResponse.class));

        ProductResponse result = productService.addProduct(validRequest, imageFile);

        assertThat(result).isNotNull();
        ArgumentCaptor<Product> productCaptor = ArgumentCaptor.forClass(Product.class);
        verify(productRepository).save(productCaptor.capture());

        Product savedProduct = productCaptor.getValue();
        assertThat(savedProduct.getVenue()).isEqualTo(venue);
        assertThat(savedProduct.getImageUrl()).isEqualTo("new_image_url.jpg");
        assertThat(savedProduct.getInStock()).isFalse();
        assertThat(savedProduct.getDeleted()).isFalse();
        assertThat(savedProduct.getHasModifiers()).isTrue();
    }

    @Test
    void addProduct_Success_WithoutImage() throws IOException {
        when(userDetailsService.getCurrentUser()).thenReturn(venueOwner);
        when(venueRepository.findByOwner_Id(1L)).thenReturn(Optional.of(venue));
        when(productMapper.toEntity(validRequest)).thenReturn(new Product());
        when(productRepository.save(any(Product.class))).thenReturn(product);

        productService.addProduct(validRequest, null);

        ArgumentCaptor<Product> productCaptor = ArgumentCaptor.forClass(Product.class);
        verify(productRepository).save(productCaptor.capture());
        assertThat(productCaptor.getValue().getImageUrl()).isNull();
        verify(fileService, never()).saveFile(any());
    }

    @Test
    void addProduct_NotVenueOwner_ThrowsException() {
        User client = new User();
        client.setId(2L);
        client.setRole(UserRole.CLIENT);
        when(userDetailsService.getCurrentUser()).thenReturn(client);

        assertThatThrownBy(() -> productService.addProduct(validRequest, null))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void addProduct_NegativePrice_ThrowsException() {
        ProductRequest invalidRequest = new ProductRequest("Test", BigDecimal.valueOf(-10), null, null, true, null, false);
        when(userDetailsService.getCurrentUser()).thenReturn(venueOwner);

        assertThatThrownBy(() -> productService.addProduct(invalidRequest, null))
                .isInstanceOf(BusinessLogicException.class);
    }

    @Test
    void updateProduct_Success_WithNewImage() throws IOException {
        when(userDetailsService.getCurrentUser()).thenReturn(venueOwner);
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(fileService.saveFile(imageFile)).thenReturn("new_image_url.jpg");
        when(productRepository.save(any(Product.class))).thenReturn(product);

        productService.updateProduct(1L, validRequest, imageFile);

        verify(fileService).deleteFile("old_image_url.jpg");
        verify(fileService).saveFile(imageFile);
        verify(productMapper).updateProductFromRequest(validRequest, product);
        verify(productRepository).save(product);
        assertThat(product.getImageUrl()).isEqualTo("new_image_url.jpg");
        assertThat(product.getVenue()).isEqualTo(venue);
    }

    @Test
    void updateProduct_Success_WithoutImage() throws IOException {
        when(userDetailsService.getCurrentUser()).thenReturn(venueOwner);
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(productRepository.save(any(Product.class))).thenReturn(product);

        productService.updateProduct(1L, validRequest, null);

        verify(fileService, never()).deleteFile(anyString());
        verify(fileService, never()).saveFile(any());
        verify(productMapper).updateProductFromRequest(validRequest, product);
        verify(productRepository).save(product);
    }

    @Test
    void updateProduct_WrongOwner_ThrowsException() {
        User anotherOwner = new User();
        anotherOwner.setId(99L);
        anotherOwner.setRole(UserRole.VENUE_OWNER);
        when(userDetailsService.getCurrentUser()).thenReturn(anotherOwner);
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));

        assertThatThrownBy(() -> productService.updateProduct(1L, validRequest, null))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void deleteProduct_Success() {
        when(userDetailsService.getCurrentUser()).thenReturn(venueOwner);
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));

        productService.deleteProduct(1L);

        verify(fileService).deleteFile("old_image_url.jpg");
        verify(productRepository).save(product);
        assertThat(product.getDeleted()).isTrue();
        assertThat(product.getInStock()).isFalse();
        assertThat(product.getImageUrl()).isNull();
    }

    @Test
    void toggleStock_Success() {
        when(userDetailsService.getCurrentUser()).thenReturn(venueOwner);
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));

        boolean initialStock = product.getInStock();
        productService.toggleStock(1L);

        verify(productRepository).save(product);
        assertThat(product.getInStock()).isNotEqualTo(initialStock);
    }
}