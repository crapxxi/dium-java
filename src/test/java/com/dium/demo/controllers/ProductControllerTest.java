package com.dium.demo.controllers;

import com.dium.demo.dto.requests.ModifierGroupRequest;
import com.dium.demo.dto.requests.ModifierRequest;
import com.dium.demo.dto.requests.ProductRequest;
import com.dium.demo.dto.responses.ModifierGroupResponse;
import com.dium.demo.dto.responses.ModifierResponse;
import com.dium.demo.dto.responses.ProductResponse;
import com.dium.demo.enums.ProductCategory;
import com.dium.demo.services.CustomUserDetailsService;
import com.dium.demo.services.JwtService;
import com.dium.demo.services.ModifierService;
import com.dium.demo.services.ProductService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = ProductController.class)
@AutoConfigureMockMvc(addFilters = false)
class ProductControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean
    private ProductService productService;

    @MockitoBean
    private ModifierService modifierService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean(name = "userDetailsService")
    private CustomUserDetailsService userDetailsService;

    @Test
    void getByVenue_Success() throws Exception {
        ProductResponse response = new ProductResponse(1L, "Burger", BigDecimal.TEN, "url", "desc", true, ProductCategory.FOOD, false);
        when(productService.getMenuByVenueId(1L)).thenReturn(List.of(response));

        mockMvc.perform(get("/api/v1/products/venue/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Burger"));
    }

    @Test
    void addProduct_Success() throws Exception {
        ProductRequest request = new ProductRequest("Pizza", BigDecimal.valueOf(5000), "url", "desc", true, ProductCategory.FOOD, true);
        ProductResponse response = new ProductResponse(1L, "Pizza", BigDecimal.valueOf(5000), "url", "desc", true, ProductCategory.FOOD, true);

        MockMultipartFile productPart = new MockMultipartFile("productRequest", "", "application/json", objectMapper.writeValueAsBytes(request));
        MockMultipartFile imagePart = new MockMultipartFile("image", "test.jpg", "image/jpeg", "image content".getBytes());

        when(productService.addProduct(any(ProductRequest.class), any())).thenReturn(response);

        mockMvc.perform(multipart("/api/v1/products/venue/products")
                        .file(productPart)
                        .file(imagePart))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Pizza"));
    }

    @Test
    void updateProduct_Success() throws Exception {
        ProductRequest request = new ProductRequest(
                "Updated Name",
                BigDecimal.valueOf(100),
                null,
                null,
                true,
                ProductCategory.FOOD,
                false
        );

        ProductResponse response = new ProductResponse(
                1L,
                "Updated Name",
                BigDecimal.valueOf(100),
                null,
                null,
                true,
                ProductCategory.FOOD,
                false
        );

        MockMultipartFile productPart = new MockMultipartFile(
                "productRequest",
                "",
                "application/json",
                objectMapper.writeValueAsBytes(request)
        );

        when(productService.updateProduct(eq(1L), any(ProductRequest.class), any())).thenReturn(response);

        mockMvc.perform(multipart("/api/v1/products/1")
                        .file(productPart)
                        .with(req -> { req.setMethod("PUT"); return req; }))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Updated Name"))
                .andExpect(jsonPath("$.category").value(ProductCategory.FOOD.toString()));
    }

    @Test
    void deleteProduct_Success() throws Exception {
        doNothing().when(productService).deleteProduct(1L);

        mockMvc.perform(delete("/api/v1/products/1"))
                .andExpect(status().isOk());
    }

    @Test
    void toggleStock_Success() throws Exception {
        doNothing().when(productService).toggleStock(1L);

        mockMvc.perform(patch("/api/v1/products/1"))
                .andExpect(status().isOk());
    }

    @Test
    void addModifierGroup_Success() throws Exception {
        ModifierResponse mod = new ModifierResponse(1L, "Cheese", BigDecimal.ONE, true);
        ModifierRequest modReq = new ModifierRequest("Cheese", BigDecimal.ONE, true);
        ModifierGroupRequest request = new ModifierGroupRequest("Extras", true, 1, 1, List.of(modReq));
        ModifierGroupResponse response = new ModifierGroupResponse(1L, "Extras", true, 1, 1, List.of(mod));

        when(modifierService.addModifierGroup(eq(1L), any(ModifierGroupRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/products/1/modifier-groups")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Extras"));
    }

    @Test
    void getModifierGroups_Success() throws Exception {
        when(modifierService.getModifierGroups(1L)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/products/1/modifier-groups"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }
}