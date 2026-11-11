package com.dium.demo.services.product;

import com.dium.demo.dto.requests.ProductRequest;
import com.dium.demo.dto.responses.ProductResponse;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

public interface ProductService {
    List<ProductResponse> getMenuByVenueId(Long venueId);
    ProductResponse addProduct(ProductRequest request, MultipartFile image) throws IOException;
    ProductResponse updateProduct(Long productId, ProductRequest request, MultipartFile image) throws IOException;
    void deleteProduct(Long productId);
    void toggleStock(Long productId);
}
